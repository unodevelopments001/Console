package com.unodevelopments.cblsshmngr.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.data.ConnectionRepository
import kotlinx.coroutines.launch

class ConnectionFormViewModel(
    private val repository: ConnectionRepository,
    private val connectionId: Long?,
) : ViewModel() {
    val editing: Boolean = connectionId != null
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var port by mutableStateOf("22")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf(false)

    init {
        val id = connectionId
        if (id != null) {
            viewModelScope.launch {
                val existing = repository.get(id) ?: return@launch
                name = existing.name
                host = existing.host
                port = existing.port.toString()
                username = existing.username
            }
        }
    }

    fun save(messages: FormMessages) {
        val portNumber = port.toIntOrNull()
        error = when {
            name.isBlank() -> messages.nickname
            host.isBlank() -> messages.host
            portNumber == null || portNumber !in 1..65535 -> messages.port
            username.isBlank() -> messages.username
            connectionId == null && password.isEmpty() -> messages.password
            else -> null
        }
        if (error != null || saving) return
        viewModelScope.launch {
            saving = true
            try {
                if (connectionId == null) {
                    repository.create(name, host, portNumber!!, username, password)
                } else {
                    repository.update(
                        id = connectionId,
                        name = name,
                        host = host,
                        port = portNumber!!,
                        username = username,
                        password = password.ifEmpty { null },
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
        fun factory(
            repository: ConnectionRepository,
            connectionId: Long?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectionFormViewModel(repository, connectionId) }
        }
    }
}

data class FormMessages(
    val nickname: String,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val generic: String,
)
