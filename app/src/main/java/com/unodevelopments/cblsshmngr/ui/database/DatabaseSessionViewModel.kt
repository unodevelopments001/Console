package com.unodevelopments.cblsshmngr.ui.database

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.data.QueryHistory
import com.unodevelopments.cblsshmngr.data.SqlRepository
import com.unodevelopments.cblsshmngr.sql.DbObject
import com.unodevelopments.cblsshmngr.sql.QueryResult
import com.unodevelopments.cblsshmngr.sql.SqlServerSession
import com.unodevelopments.cblsshmngr.sql.TableData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.SQLException
import kotlin.coroutines.cancellation.CancellationException

sealed interface DbScreen {
    data object Databases : DbScreen
    data class Objects(val database: String) : DbScreen
    data class Table(val database: String, val schema: String, val name: String, val view: Boolean = false) : DbScreen
    data class Procedure(
        val database: String,
        val schema: String,
        val name: String,
        val function: Boolean = false,
    ) : DbScreen
    data class Query(val database: String) : DbScreen
}

enum class DbObjectKind { Table, View, Procedure, Function }

sealed interface DbPhase {
    data object Connecting : DbPhase
    data class Failed(val message: String, val canRetry: Boolean = false) : DbPhase
    data object Ready : DbPhase
}

class DatabaseSessionViewModel(
    application: Application,
    private val repository: SqlRepository,
    private val connectionId: Long,
) : AndroidViewModel(application) {
    private val gate = Mutex()
    private var sql: SqlServerSession? = null
    private var connectJob: Job? = null
    private var generation = 0
    private val stack = ArrayDeque<DbScreen>()

    private val _phase = MutableStateFlow<DbPhase>(DbPhase.Connecting)
    val phase = _phase.asStateFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _screen = MutableStateFlow<DbScreen>(DbScreen.Databases)
    val screen = _screen.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _databases = MutableStateFlow<List<String>>(emptyList())
    val databases = _databases.asStateFlow()

    private val _tables = MutableStateFlow<List<DbObject>>(emptyList())
    val tables = _tables.asStateFlow()

    private val _views = MutableStateFlow<List<DbObject>>(emptyList())
    val views = _views.asStateFlow()

    private val _procedures = MutableStateFlow<List<DbObject>>(emptyList())
    val procedures = _procedures.asStateFlow()

    private val _functions = MutableStateFlow<List<DbObject>>(emptyList())
    val functions = _functions.asStateFlow()

    private val _table = MutableStateFlow<TableData?>(null)
    val table = _table.asStateFlow()

    private val _script = MutableStateFlow("")
    val script = _script.asStateFlow()

    private val _querySql = MutableStateFlow("SELECT 1")
    val querySql = _querySql.asStateFlow()

    private val _queryResult = MutableStateFlow<QueryResult?>(null)
    val queryResult = _queryResult.asStateFlow()

    private val _history = MutableStateFlow<List<QueryHistory>>(emptyList())
    val history = _history.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeQueries(connectionId).collect { _history.value = it }
        }
        connect()
    }

    fun reconnect() = connect()

    fun report(message: String) {
        viewModelScope.launch { _messages.emit(message) }
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        val current = stack.last()
        _screen.value = current
        reload(current)
        return true
    }

    fun openDatabase(name: String) = push(DbScreen.Objects(name))

    fun openTable(item: DbObject) {
        val database = currentDatabase() ?: return
        push(DbScreen.Table(database, item.schema, item.name))
    }

    fun openView(item: DbObject) {
        val database = currentDatabase() ?: return
        push(DbScreen.Table(database, item.schema, item.name, view = true))
    }

    fun openProcedure(item: DbObject) {
        val database = currentDatabase() ?: return
        push(DbScreen.Procedure(database, item.schema, item.name))
    }

    fun openFunction(item: DbObject) {
        val database = currentDatabase() ?: return
        push(DbScreen.Procedure(database, item.schema, item.name, function = true))
    }

    fun openQuery() {
        val database = currentDatabase().orEmpty()
        push(DbScreen.Query(database))
    }

    fun updateScript(value: String) {
        _script.value = value
    }

    fun updateQuery(value: String) {
        _querySql.value = value
    }

    fun useHistory(sql: String) {
        _querySql.value = sql
        runQuery()
    }

    fun refresh() = reload(_screen.value)

    fun dropDatabase(name: String) = operate(text(R.string.drop)) {
        sql?.dropDatabase(name)
        _databases.value = sql?.databases().orEmpty()
    }

    fun dropListed(kind: DbObjectKind, item: DbObject) {
        val database = currentDatabase() ?: return
        operate(text(R.string.drop)) {
            val session = sql ?: return@operate
            when (kind) {
                DbObjectKind.Table -> session.dropTable(database, item.schema, item.name)
                DbObjectKind.View -> session.dropView(database, item.schema, item.name)
                DbObjectKind.Procedure -> session.dropProcedure(database, item.schema, item.name)
                DbObjectKind.Function -> session.dropFunction(database, item.schema, item.name)
            }
            refreshObjects(session, database)
        }
    }

    fun truncateTable() {
        val current = _screen.value as? DbScreen.Table ?: return
        operate(text(R.string.truncate)) {
            sql?.truncate(current.database, current.schema, current.name)
            _table.value = sql?.loadTable(current.database, current.schema, current.name, 0)
        }
    }

    fun dropTable() {
        val current = _screen.value as? DbScreen.Table ?: return
        operate(text(R.string.drop)) {
            if (current.view) sql?.dropView(current.database, current.schema, current.name)
            else sql?.dropTable(current.database, current.schema, current.name)
            stack.removeLast()
            val parent = stack.last()
            _screen.value = parent
            reloadSync(parent)
        }
    }

    fun dropProcedure() {
        val current = _screen.value as? DbScreen.Procedure ?: return
        operate(text(R.string.drop)) {
            if (current.function) sql?.dropFunction(current.database, current.schema, current.name)
            else sql?.dropProcedure(current.database, current.schema, current.name)
            stack.removeLast()
            val parent = stack.last()
            _screen.value = parent
            reloadSync(parent)
        }
    }

    fun saveRow(original: List<String?>?, edited: List<String?>) {
        val current = _screen.value as? DbScreen.Table ?: return
        val data = _table.value ?: return
        operate(text(R.string.save)) {
            val session = sql ?: return@operate
            try {
                session.saveRow(current.database, current.schema, current.name, data.columns, data.primaryKeys, original, edited)
            } catch (error: IllegalStateException) {
                if (error.message == "NO_PRIMARY_KEY") throw SqlNotice(text(R.string.no_primary_key))
                throw error
            }
            _table.value = session.loadTable(current.database, current.schema, current.name, 0)
        }
    }

    fun deleteRow(row: List<String?>) {
        val current = _screen.value as? DbScreen.Table ?: return
        val data = _table.value ?: return
        operate(text(R.string.delete)) {
            val session = sql ?: return@operate
            try {
                session.deleteRow(current.database, current.schema, current.name, data.columns, data.primaryKeys, row)
            } catch (error: IllegalStateException) {
                if (error.message == "NO_PRIMARY_KEY") throw SqlNotice(text(R.string.no_primary_key))
                throw error
            }
            _table.value = session.loadTable(current.database, current.schema, current.name, 0)
        }
    }

    fun saveProcedure() {
        val current = _screen.value as? DbScreen.Procedure ?: return
        val script = asAlter(_script.value, current.function)
        operate(text(R.string.save)) {
            sql?.execute(current.database, script)
            _script.value = sql?.procedureScript(current.database, current.schema, current.name) ?: script
        }
    }

    fun runQuery() {
        val current = _screen.value as? DbScreen.Query ?: return
        operate(text(R.string.run)) {
            val statement = _querySql.value
            val result = sql?.execute(current.database, statement) ?: return@operate
            _queryResult.value = result
            repository.rememberQuery(connectionId, statement)
            if (result is QueryResult.Affected) {
                _messages.emit(text(R.string.rows_affected, result.count))
            }
        }
    }

    fun disconnect() {
        val session = sql
        sql = null
        Thread { session?.close() }.start()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    private fun connect() {
        val ticket = ++generation
        connectJob?.cancel()
        val previous = sql
        sql = null
        previous?.let { Thread { it.close() }.start() }
        connectJob = viewModelScope.launch {
            _phase.value = DbPhase.Connecting
            val saved = repository.get(connectionId)
            if (saved == null) {
                _phase.value = DbPhase.Failed(text(R.string.error_missing_connection))
                return@launch
            }
            _title.value = saved.name
            val ssh = repository.ssh(saved.sshConnectionId)
            if (ssh == null) {
                _phase.value = DbPhase.Failed(text(R.string.error_ssh_missing))
                return@launch
            }
            val sshPassword = try {
                repository.decryptSshPassword(ssh).toCharArray()
            } catch (_: Exception) {
                _phase.value = DbPhase.Failed(text(R.string.error_password_read))
                return@launch
            }
            val sqlPassword = try {
                repository.decryptPassword(saved)
            } catch (_: Exception) {
                sshPassword.fill('\u0000')
                _phase.value = DbPhase.Failed(text(R.string.error_password_read))
                return@launch
            }
            val session = SqlServerSession()
            sql = session
            try {
                val trusted = withContext(Dispatchers.IO) {
                    try {
                        session.connect(
                            sshHost = ssh.host,
                            sshPort = ssh.port,
                            sshUser = ssh.username,
                            sshPassword = sshPassword,
                            knownFingerprint = ssh.hostKeyFingerprint,
                            sqlHost = saved.sqlHost,
                            sqlPort = saved.sqlPort,
                            sqlUser = saved.username,
                            sqlPassword = sqlPassword,
                            database = "",
                        )
                    } finally {
                        sshPassword.fill('\u0000')
                    }
                }
                if (ticket != generation) return@launch
                if (trusted != null) repository.trustHostKey(ssh.id, trusted)
                stack.clear()
                stack.addLast(DbScreen.Databases)
                _screen.value = DbScreen.Databases
                _databases.value = withContext(Dispatchers.IO) { session.databases() }
                if (ticket != generation) return@launch
                _phase.value = DbPhase.Ready
            } catch (error: CancellationException) {
                session.close()
                if (sql === session) sql = null
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "SQL Server connect failed", error)
                session.close()
                if (sql === session) sql = null
                if (ticket != generation) return@launch
                val message = if (session.hostKeyFailure) {
                    text(R.string.host_key_mismatch_db)
                } else {
                    error.message?.takeIf { it.isNotBlank() } ?: text(R.string.error_generic)
                }
                _phase.value = DbPhase.Failed(message, canRetry = true)
            }
        }
    }

    private fun push(screen: DbScreen) {
        stack.addLast(screen)
        _screen.value = screen
        reload(screen)
    }

    private fun reload(screen: DbScreen) {
        viewModelScope.launch {
            gate.withLock {
                _loading.value = true
                try {
                    withContext(Dispatchers.IO) { reloadSync(screen) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "SQL reload failed", error)
                    if (lostConnection(error)) failConnection() else _messages.emit(friendly(error))
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    private fun reloadSync(screen: DbScreen) {
        val session = sql ?: return
        when (screen) {
            DbScreen.Databases -> _databases.value = session.databases()
            is DbScreen.Objects -> refreshObjects(session, screen.database)
            is DbScreen.Table -> _table.value = session.loadTable(screen.database, screen.schema, screen.name, 0)
            is DbScreen.Procedure -> {
                try {
                    _script.value = session.procedureScript(screen.database, screen.schema, screen.name)
                } catch (error: IllegalStateException) {
                    if (error.message == "ENCRYPTED") {
                        _script.value = ""
                        _messages.tryEmit(text(R.string.encrypted_procedure))
                    } else {
                        throw error
                    }
                }
            }
            is DbScreen.Query -> _queryResult.value = null
        }
    }

    private fun operate(done: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            gate.withLock {
                _loading.value = true
                try {
                    withContext(Dispatchers.IO) { block() }
                    _messages.emit(done)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "SQL operation failed", error)
                    if (lostConnection(error)) failConnection() else _messages.emit(friendly(error))
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    private fun refreshObjects(session: SqlServerSession, database: String) {
        _tables.value = session.tables(database)
        _views.value = session.views(database)
        _procedures.value = session.procedures(database)
        _functions.value = session.functions(database)
    }

    private fun failConnection() {
        val session = sql
        sql = null
        session?.let { Thread { it.close() }.start() }
        _phase.value = DbPhase.Failed(text(R.string.session_dropped), canRetry = true)
    }

    private fun lostConnection(error: Throwable): Boolean {
        val blob = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return blob.contains("i/o error") ||
            blob.contains("connection is closed") ||
            blob.contains("socket closed") ||
            blob.contains("connection reset") ||
            blob.contains("broken pipe") ||
            blob.contains("connection abort")
    }

    private fun currentDatabase(): String? = when (val current = _screen.value) {
        is DbScreen.Objects -> current.database
        is DbScreen.Table -> current.database
        is DbScreen.Procedure -> current.database
        is DbScreen.Query -> current.database
        DbScreen.Databases -> null
    }

    private fun friendly(error: Throwable): String {
        val notice = generateSequence(error) { it.cause }.filterIsInstance<SqlNotice>().firstOrNull()
        if (notice != null) return notice.message ?: text(R.string.error_generic)
        val sqlError = generateSequence(error) { it.cause }.filterIsInstance<SQLException>().firstOrNull()
        return sqlError?.message?.takeIf { it.isNotBlank() }
            ?: error.message?.takeIf { it.isNotBlank() }
            ?: text(R.string.error_generic)
    }

    private fun text(id: Int, vararg args: Any): String {
        return getApplication<Application>().getString(id, *args)
    }

    companion object {
        private const val TAG = "CabalSQL"

        fun factory(application: Application, repository: SqlRepository, id: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DatabaseSessionViewModel(application, repository, id) }
            }

        fun asAlter(script: String, function: Boolean = false): String {
            val pattern = if (function) {
                Regex("(?i)\\bCREATE\\s+(OR\\s+ALTER\\s+)?FUNCTION\\b")
            } else {
                Regex("(?i)\\bCREATE\\s+(OR\\s+ALTER\\s+)?PROC(EDURE)?\\b")
            }
            val replacement = if (function) "ALTER FUNCTION" else "ALTER PROCEDURE"
            return if (pattern.containsMatchIn(script)) pattern.replaceFirst(script, replacement) else script
        }
    }
}

private class SqlNotice(message: String) : Exception(message)
