package com.unodevelopments.cblsshmngr.data

import kotlinx.coroutines.flow.Flow

class ConnectionRepository(
    private val dao: ConnectionDao,
    private val cipher: PasswordCipher,
    private val commands: SavedCommandDao,
) {
    fun observeCommands(sshConnectionId: Long): Flow<List<SavedCommand>> = commands.observe(sshConnectionId)

    suspend fun addCommand(sshConnectionId: Long, label: String, command: String) {
        val title = label.trim()
        val body = command.trim()
        if (title.isEmpty() || body.isEmpty()) return
        commands.insert(SavedCommand(sshConnectionId = sshConnectionId, label = title, command = body))
    }

    suspend fun deleteCommand(id: Long) {
        commands.delete(id)
    }
    fun observeAll(): Flow<List<SshConnection>> = dao.observeAll()

    suspend fun get(id: Long): SshConnection? = dao.get(id)

    suspend fun create(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
    ): Long {
        return dao.insert(
            SshConnection(
                name = name.trim(),
                host = host.trim(),
                port = port,
                username = username.trim(),
                passwordCipher = cipher.encrypt(password),
            ),
        )
    }

    suspend fun update(
        id: Long,
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String?,
    ) {
        val existing = dao.get(id) ?: error("Missing connection")
        val passwordCipher = if (password.isNullOrEmpty()) {
            existing.passwordCipher
        } else {
            cipher.encrypt(password)
        }
        dao.update(
            existing.copy(
                name = name.trim(),
                host = host.trim(),
                port = port,
                username = username.trim(),
                passwordCipher = passwordCipher,
            ),
        )
    }

    suspend fun delete(id: Long) {
        commands.deleteForConnection(id)
        dao.delete(id)
    }

    fun decryptPassword(connection: SshConnection): String = cipher.decrypt(connection.passwordCipher)

    suspend fun trustHostKey(id: Long, fingerprint: String) {
        dao.updateFingerprint(id, fingerprint)
    }

    suspend fun clearHostKey(id: Long) {
        dao.clearFingerprint(id)
    }
}
