package com.unodevelopments.cblsshmngr.sql

import com.unodevelopments.cblsshmngr.ssh.CryptoSetup
import com.unodevelopments.cblsshmngr.ssh.Fingerprints
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.sourceforge.jtds.jdbc.Driver
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.PublicKey
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

data class DbObject(val schema: String, val name: String) {
    val label: String get() = "$schema.$name"
}

data class ColumnInfo(val name: String, val identity: Boolean)

data class TableData(
    val columns: List<ColumnInfo>,
    val primaryKeys: List<String>,
    val rows: List<List<String?>>,
)

data class ProcedureData(val schema: String, val name: String, val script: String)

sealed interface QueryResult {
    data class Grid(val columns: List<String>, val rows: List<List<String?>>) : QueryResult
    data class Affected(val count: Int) : QueryResult
}

class SqlServerSession {
    private val closed = AtomicBoolean(false)
    private var client: SSHClient? = null
    private var serverSocket: ServerSocket? = null
    private var forwarder: LocalPortForwarder? = null
    private var forwardThread: Thread? = null
    private var jdbc: Connection? = null
    private val driver = Driver()

    var hostKeyFailure: Boolean = false
        private set

    fun connect(
        sshHost: String,
        sshPort: Int,
        sshUser: String,
        sshPassword: CharArray,
        knownFingerprint: String?,
        sqlHost: String,
        sqlPort: Int,
        sqlUser: String,
        sqlPassword: String,
        database: String,
    ): String? {
        CryptoSetup.install()
        hostKeyFailure = false
        var accepted: String? = null
        val ssh = SSHClient()
        client = ssh
        ssh.connectTimeout = 20_000
        ssh.timeout = 0
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = Fingerprints.sha256(key)
                if (knownFingerprint == null) {
                    accepted = fingerprint
                    return true
                }
                if (knownFingerprint != fingerprint) {
                    hostKeyFailure = true
                    return false
                }
                return true
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })
        try {
            ssh.connect(sshHost, sshPort)
            ssh.authPassword(sshUser, sshPassword)
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            serverSocket = socket
            val tunnel = ssh.newLocalPortForwarder(
                Parameters("127.0.0.1", socket.localPort, sqlHost, sqlPort),
                socket,
            )
            forwarder = tunnel
            val thread = Thread {
                try {
                    tunnel.listen()
                } catch (_: Exception) {
                }
            }
            thread.isDaemon = true
            thread.name = "sql-tunnel"
            thread.start()
            forwardThread = thread
            jdbc = openJdbc(socket.localPort, sqlUser, sqlPassword, database)
            return accepted
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun databases(): List<String> = withJdbc { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sys.databases WHERE state = 0 ORDER BY name",
            ).use { results ->
                val names = mutableListOf<String>()
                while (results.next()) names += results.getString(1)
                names
            }
        }
    }

    fun tables(database: String): List<DbObject> = withJdbc { connection ->
        connection.catalog = database
        queryObjects(
            connection,
            """
            SELECT TABLE_SCHEMA, TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_SCHEMA, TABLE_NAME
            """.trimIndent(),
        )
    }

    fun procedures(database: String): List<DbObject> = withJdbc { connection ->
        connection.catalog = database
        queryObjects(
            connection,
            """
            SELECT ROUTINE_SCHEMA, ROUTINE_NAME
            FROM INFORMATION_SCHEMA.ROUTINES
            WHERE ROUTINE_TYPE = 'PROCEDURE'
            ORDER BY ROUTINE_SCHEMA, ROUTINE_NAME
            """.trimIndent(),
        )
    }

    fun views(database: String): List<DbObject> = withJdbc { connection ->
        connection.catalog = database
        queryObjects(
            connection,
            """
            SELECT TABLE_SCHEMA, TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_TYPE = 'VIEW'
            ORDER BY TABLE_SCHEMA, TABLE_NAME
            """.trimIndent(),
        )
    }

    fun functions(database: String): List<DbObject> = withJdbc { connection ->
        connection.catalog = database
        queryObjects(
            connection,
            """
            SELECT ROUTINE_SCHEMA, ROUTINE_NAME
            FROM INFORMATION_SCHEMA.ROUTINES
            WHERE ROUTINE_TYPE = 'FUNCTION'
            ORDER BY ROUTINE_SCHEMA, ROUTINE_NAME
            """.trimIndent(),
        )
    }

    fun loadTable(database: String, schema: String, table: String, offset: Int): TableData = withJdbc { connection ->
        connection.catalog = database
        val columns = columnInfo(connection, schema, table)
        val keys = primaryKeys(connection, schema, table)
        val sql = "SELECT * FROM ${quote(schema)}.${quote(table)} ORDER BY (SELECT NULL) OFFSET $offset ROWS FETCH NEXT $PAGE ROWS ONLY"
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { results ->
                val names = (1..results.metaData.columnCount).map { results.metaData.getColumnName(it) }
                val rows = mutableListOf<List<String?>>()
                while (results.next()) {
                    rows += names.indices.map { index ->
                        val value = results.getString(index + 1)
                        if (results.wasNull()) null else value
                    }
                }
                val described = names.map { name ->
                    columns.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ColumnInfo(name, identity = false)
                }
                TableData(described, keys, rows)
            }
        }
    }

    fun procedureScript(database: String, schema: String, name: String): String = withJdbc { connection ->
        connection.catalog = database
        connection.prepareStatement(
            "SELECT OBJECT_DEFINITION(OBJECT_ID(?))",
        ).use { statement ->
            statement.setString(1, "$schema.$name")
            statement.executeQuery().use { results ->
                if (!results.next()) error("Procedure not found")
                results.getString(1) ?: error("ENCRYPTED")
            }
        }
    }

    fun execute(database: String, sql: String): QueryResult = withJdbc { connection ->
        if (database.isNotBlank()) connection.catalog = database
        val batches = sqlBatches(sql)
        var last: QueryResult = QueryResult.Affected(0)
        connection.createStatement().use { statement ->
            statement.maxRows = PAGE
            for (batch in batches) {
                val hasRows = statement.execute(batch)
                last = if (hasRows) readGrid(statement.resultSet) else QueryResult.Affected(statement.updateCount.coerceAtLeast(0))
            }
        }
        last
    }

    fun saveRow(
        database: String,
        schema: String,
        table: String,
        columns: List<ColumnInfo>,
        primaryKeys: List<String>,
        original: List<String?>?,
        edited: List<String?>,
    ) = withJdbc { connection ->
        connection.catalog = database
        if (original == null) {
            val insertable = columns.mapIndexedNotNull { index, column ->
                if (column.identity) null else index to column
            }
            val sql = "INSERT INTO ${quote(schema)}.${quote(table)} (" +
                insertable.joinToString { quote(it.second.name) } +
                ") VALUES (" + insertable.joinToString { "?" } + ")"
            connection.prepareStatement(sql).use { statement ->
                insertable.forEachIndexed { parameter, (index, _) -> bind(statement, parameter + 1, edited.getOrNull(index)) }
                statement.executeUpdate()
            }
        } else {
            if (primaryKeys.isEmpty()) error("NO_PRIMARY_KEY")
            val editable = columns.mapIndexedNotNull { index, column ->
                if (column.identity) null else index to column
            }
            val where = primaryKeys.joinToString(" AND ") { "${quote(it)} = ?" }
            val sql = "UPDATE ${quote(schema)}.${quote(table)} SET " +
                editable.joinToString { "${quote(it.second.name)} = ?" } +
                " WHERE $where"
            connection.prepareStatement(sql).use { statement ->
                var parameter = 1
                editable.forEach { (index, _) -> bind(statement, parameter++, edited.getOrNull(index)) }
                primaryKeys.forEach { key ->
                    val index = columns.indexOfFirst { it.name.equals(key, ignoreCase = true) }
                    bind(statement, parameter++, original.getOrNull(index))
                }
                statement.executeUpdate()
            }
        }
    }

    fun deleteRow(
        database: String,
        schema: String,
        table: String,
        columns: List<ColumnInfo>,
        primaryKeys: List<String>,
        row: List<String?>,
    ) = withJdbc { connection ->
        connection.catalog = database
        if (primaryKeys.isEmpty()) error("NO_PRIMARY_KEY")
        val where = primaryKeys.joinToString(" AND ") { "${quote(it)} = ?" }
        val sql = "DELETE FROM ${quote(schema)}.${quote(table)} WHERE $where"
        connection.prepareStatement(sql).use { statement ->
            primaryKeys.forEachIndexed { parameter, key ->
                val index = columns.indexOfFirst { it.name.equals(key, ignoreCase = true) }
                bind(statement, parameter + 1, row.getOrNull(index))
            }
            statement.executeUpdate()
        }
    }

    fun truncate(database: String, schema: String, table: String) {
        execute(database, "TRUNCATE TABLE ${quote(schema)}.${quote(table)}")
    }

    fun dropTable(database: String, schema: String, table: String) {
        execute(database, "DROP TABLE ${quote(schema)}.${quote(table)}")
    }

    fun dropProcedure(database: String, schema: String, name: String) {
        execute(database, "DROP PROCEDURE ${quote(schema)}.${quote(name)}")
    }

    fun dropView(database: String, schema: String, name: String) {
        execute(database, "DROP VIEW ${quote(schema)}.${quote(name)}")
    }

    fun dropFunction(database: String, schema: String, name: String) {
        execute(database, "DROP FUNCTION ${quote(schema)}.${quote(name)}")
    }

    fun dropDatabase(name: String) {
        execute("", "DROP DATABASE ${quote(name)}")
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            jdbc?.close()
        } catch (_: Exception) {
        }
        try {
            forwarder?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
        forwardThread?.interrupt()
        jdbc = null
        forwarder = null
        serverSocket = null
        client = null
    }

    private fun openJdbc(port: Int, user: String, password: String, database: String): Connection {
        val url = buildString {
            append("jdbc:jtds:sqlserver://127.0.0.1:")
            append(port)
            if (database.isNotBlank()) {
                append('/')
                append(database)
            }
        }
        val properties = Properties()
        properties["user"] = user
        properties["password"] = password
        val first = runCatching { driver.connect(url, properties) }
        val connection = first.getOrNull() ?: run {
            Thread.sleep(400)
            driver.connect(url, properties)
        }
        return connection ?: error("SQL Server did not accept the connection")
    }

    private fun <T> withJdbc(block: (Connection) -> T): T {
        val connection = jdbc ?: error("Not connected")
        return block(connection)
    }

    private fun queryObjects(connection: Connection, sql: String): List<DbObject> {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { results ->
                val objects = mutableListOf<DbObject>()
                while (results.next()) objects += DbObject(results.getString(1), results.getString(2))
                return objects
            }
        }
    }

    private fun columnInfo(connection: Connection, schema: String, table: String): List<ColumnInfo> {
        connection.prepareStatement(
            """
            SELECT c.name, c.is_identity
            FROM sys.columns c
            JOIN sys.tables t ON c.object_id = t.object_id
            JOIN sys.schemas s ON t.schema_id = s.schema_id
            WHERE s.name = ? AND t.name = ?
            ORDER BY c.column_id
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schema)
            statement.setString(2, table)
            statement.executeQuery().use { results ->
                val columns = mutableListOf<ColumnInfo>()
                while (results.next()) columns += ColumnInfo(results.getString(1), results.getBoolean(2))
                return columns
            }
        }
    }

    private fun primaryKeys(connection: Connection, schema: String, table: String): List<String> {
        connection.prepareStatement(
            """
            SELECT c.COLUMN_NAME
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
            JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE c
              ON tc.CONSTRAINT_NAME = c.CONSTRAINT_NAME
             AND tc.TABLE_SCHEMA = c.TABLE_SCHEMA
             AND tc.TABLE_NAME = c.TABLE_NAME
            WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'
              AND tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ?
            ORDER BY c.ORDINAL_POSITION
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schema)
            statement.setString(2, table)
            statement.executeQuery().use { results ->
                val keys = mutableListOf<String>()
                while (results.next()) keys += results.getString(1)
                return keys
            }
        }
    }

    private fun readGrid(results: ResultSet): QueryResult.Grid {
        results.use { rows ->
            val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it) }
            val values = mutableListOf<List<String?>>()
            while (rows.next() && values.size < PAGE) {
                values += columns.indices.map { index ->
                    val value = rows.getString(index + 1)
                    if (rows.wasNull()) null else value
                }
            }
            return QueryResult.Grid(columns, values)
        }
    }

    private fun bind(statement: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value == null) statement.setNull(index, Types.VARCHAR) else statement.setString(index, value)
    }

    private companion object {
        const val PAGE = 100

        fun quote(name: String): String = "[" + name.replace("]", "]]") + "]"

        fun sqlBatches(script: String): List<String> {
            return script.split(Regex("(?im)^\\s*GO\\s*$"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
