package com.unodevelopments.cblsshmngr.ui.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.data.SqlRepository
import com.unodevelopments.cblsshmngr.data.SshConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatabaseFormViewModel(
    private val repository: SqlRepository,
    private val connectionId: Long?,
) : ViewModel() {
    val editing: Boolean = connectionId != null
    val sshConnections = repository.observeSsh()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var name by mutableStateOf("")
    var sshConnectionId by mutableStateOf<Long?>(null)
    var sqlHost by mutableStateOf("127.0.0.1")
    var sqlPort by mutableStateOf("1433")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var databaseName by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf(false)

    init {
        val id = connectionId
        if (id != null) {
            viewModelScope.launch {
                val existing = repository.get(id) ?: return@launch
                name = existing.name
                sshConnectionId = existing.sshConnectionId
                sqlHost = existing.sqlHost
                sqlPort = existing.sqlPort.toString()
                username = existing.username
                databaseName = existing.databaseName
            }
        }
    }

    fun sshLabel(connections: List<SshConnection>): String {
        val id = sshConnectionId ?: return ""
        return connections.firstOrNull { it.id == id }?.name.orEmpty()
    }

    fun save(messages: DatabaseFormMessages) {
        val portNumber = sqlPort.toIntOrNull()
        val sshId = sshConnectionId
        error = when {
            name.isBlank() -> messages.nickname
            sshId == null -> messages.ssh
            portNumber == null || portNumber !in 1..65535 -> messages.port
            username.isBlank() -> messages.username
            connectionId == null && password.isEmpty() -> messages.password
            else -> null
        }
        if (error != null || saving || sshId == null) return
        viewModelScope.launch {
            saving = true
            try {
                if (connectionId == null) {
                    repository.create(name, sshId, sqlHost, portNumber!!, username, password, "")
                } else {
                    repository.update(
                        id = connectionId,
                        name = name,
                        sshConnectionId = sshId,
                        sqlHost = sqlHost,
                        sqlPort = portNumber!!,
                        username = username,
                        password = password.ifEmpty { null },
                        databaseName = "",
                    )
                }
                saved = true
            } catch (failure: Exception) {
                error = failure.message ?: messages.generic
            } finally {
                saving = false
            }
        }
    }

    companion object {
        fun factory(repository: SqlRepository, connectionId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { DatabaseFormViewModel(repository, connectionId) }
        }
    }
}

data class DatabaseFormMessages(
    val nickname: String,
    val ssh: String,
    val port: String,
    val username: String,
    val password: String,
    val generic: String,
)
