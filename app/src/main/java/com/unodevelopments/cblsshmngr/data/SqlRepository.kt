package com.unodevelopments.cblsshmngr.data

import kotlinx.coroutines.flow.Flow

class SqlRepository(
    private val sqlDao: SqlConnectionDao,
    private val sshDao: ConnectionDao,
    private val cipher: PasswordCipher,
    private val history: QueryHistoryDao,
) {
    fun observeQueries(sqlConnectionId: Long): Flow<List<QueryHistory>> = history.observe(sqlConnectionId)

    suspend fun rememberQuery(sqlConnectionId: Long, sql: String) {
        val text = sql.trim()
        if (text.isEmpty()) return
        if (history.newest(sqlConnectionId)?.sql == text) return
        history.insert(
            QueryHistory(
                sqlConnectionId = sqlConnectionId,
                sql = text,
                createdAt = System.currentTimeMillis(),
            ),
        )
        history.ids(sqlConnectionId).drop(20).forEach { history.delete(it) }
    }
    fun observeAll(): Flow<List<SqlConnection>> = sqlDao.observeAll()

    fun observeSsh(): Flow<List<SshConnection>> = sshDao.observeAll()

    suspend fun get(id: Long): SqlConnection? = sqlDao.get(id)

    suspend fun ssh(id: Long): SshConnection? = sshDao.get(id)

    suspend fun create(
        name: String,
        sshConnectionId: Long,
        sqlHost: String,
        sqlPort: Int,
        username: String,
        password: String,
        databaseName: String,
    ): Long {
        return sqlDao.insert(
            SqlConnection(
                name = name.trim(),
                sshConnectionId = sshConnectionId,
                sqlHost = sqlHost.trim().ifBlank { "127.0.0.1" },
                sqlPort = sqlPort,
                username = username.trim(),
                passwordCipher = cipher.encrypt(password),
                databaseName = databaseName.trim(),
            ),
        )
    }

    suspend fun update(
        id: Long,
        name: String,
        sshConnectionId: Long,
        sqlHost: String,
        sqlPort: Int,
        username: String,
        password: String?,
        databaseName: String,
    ) {
        val existing = sqlDao.get(id) ?: error("Missing database connection")
        val passwordCipher = if (password.isNullOrEmpty()) {
            existing.passwordCipher
        } else {
            cipher.encrypt(password)
        }
        sqlDao.update(
            existing.copy(
                name = name.trim(),
                sshConnectionId = sshConnectionId,
                sqlHost = sqlHost.trim().ifBlank { "127.0.0.1" },
                sqlPort = sqlPort,
                username = username.trim(),
                passwordCipher = passwordCipher,
                databaseName = databaseName.trim(),
            ),
        )
    }

    suspend fun delete(id: Long) {
        history.deleteForConnection(id)
        sqlDao.delete(id)
    }

    fun decryptPassword(connection: SqlConnection): String = cipher.decrypt(connection.passwordCipher)

    fun decryptSshPassword(connection: SshConnection): String = cipher.decrypt(connection.passwordCipher)

    suspend fun trustHostKey(id: Long, fingerprint: String) {
        sshDao.updateFingerprint(id, fingerprint)
    }
}
