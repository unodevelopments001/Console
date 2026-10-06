package com.unodevelopments.cblsshmngr.ui.session

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.data.ConnectionRepository
import com.unodevelopments.cblsshmngr.data.SavedCommand
import com.unodevelopments.cblsshmngr.ssh.FileTooLargeException
import com.unodevelopments.cblsshmngr.ssh.HostKeyFailure
import com.unodevelopments.cblsshmngr.ssh.RemoteFile
import com.unodevelopments.cblsshmngr.ssh.SshSession
import com.unodevelopments.cblsshmngr.ssh.Utf8Decoder
import com.unodevelopments.cblsshmngr.ui.terminal.TerminalBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.userauth.UserAuthException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.cancellation.CancellationException

const val MAX_EDIT_BYTES = 1_048_576

data class BrowserState(
    val path: String = "",
    val entries: List<RemoteFile> = emptyList(),
    val loading: Boolean = false,
)

data class EditorState(
    val path: String,
    val name: String,
    val original: String,
    val draft: String,
    val saving: Boolean = false,
)

sealed interface SessionPhase {
    data object Connecting : SessionPhase
    data class Failed(
        val message: String,
        val canRetry: Boolean,
        val canForgetHostKey: Boolean,
    ) : SessionPhase
    data object Connected : SessionPhase
}

class SessionViewModel(
    application: Application,
    private val repository: ConnectionRepository,
    private val connectionId: Long,
) : AndroidViewModel(application) {
    private val buffer = TerminalBuffer()
    private val jobs = mutableListOf<Job>()
    private var ssh: SshSession? = null
    private var trustGate: CompletableDeferred<Boolean>? = null
    private var connectJob: Job? = null

    private val _phase = MutableStateFlow<SessionPhase>(SessionPhase.Connecting)
    val phase = _phase.asStateFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _pendingFingerprint = MutableStateFlow<String?>(null)
    val pendingFingerprint = _pendingFingerprint.asStateFlow()

    private val _terminal = MutableStateFlow(AnnotatedString(""))
    val terminal = _terminal.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _browser = MutableStateFlow(BrowserState())
    val browser = _browser.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor = _editor.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _commands = MutableStateFlow<List<SavedCommand>>(emptyList())
    val commands = _commands.asStateFlow()

    private val _dropped = MutableStateFlow(false)
    val dropped = _dropped.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCommands(connectionId).collect { _commands.value = it }
        }
        connect(forgetHostKey = false)
    }

    fun addCommand(label: String, command: String) {
        viewModelScope.launch { repository.addCommand(connectionId, label, command) }
    }

    fun deleteCommand(id: Long) {
        viewModelScope.launch { repository.deleteCommand(id) }
    }

    fun sendSaved(command: String) {
        send(command.trimEnd('\r', '\n') + "\r")
    }

    fun onTrustDecision(accept: Boolean) {
        _pendingFingerprint.value = null
        trustGate?.complete(accept)
        trustGate = null
    }

    fun retry() {
        connect(forgetHostKey = false)
    }

    fun forgetHostKeyAndRetry() {
        connect(forgetHostKey = true)
    }

    fun send(text: String) {
        if (text.isEmpty()) return
        val session = ssh ?: return
        viewModelScope.launch(Dispatchers.IO) { session.send(text) }
    }

    fun resize(cols: Int, rows: Int) {
        val session = ssh ?: return
        viewModelScope.launch(Dispatchers.IO) {
            session.resize(cols, rows)
        }
    }

    fun refreshFiles() {
        val path = _browser.value.path.ifBlank { ssh?.home ?: return }
        loadDirectory(path)
    }

    fun openDirectory(path: String) {
        loadDirectory(path)
    }

    fun goUp() {
        val parent = parentPath(_browser.value.path) ?: return
        loadDirectory(parent)
    }

    fun openEntry(entry: RemoteFile) {
        val session = ssh ?: return
        viewModelScope.launch {
            _browser.value = _browser.value.copy(loading = true)
            try {
                if (entry.directory || session.isDirectory(entry.path)) {
                    val entries = session.list(entry.path)
                    _browser.value = BrowserState(entry.path, entries, loading = false)
                } else {
                    openEditor(session, entry)
                    _browser.value = _browser.value.copy(loading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not open ${entry.path}", error)
                _browser.value = _browser.value.copy(loading = false)
                _messages.emit(error.friendly())
            }
        }
    }

    fun updateDraft(value: String) {
        val current = _editor.value ?: return
        _editor.value = current.copy(draft = value)
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun saveEditor() {
        val current = _editor.value ?: return
        val session = ssh ?: return
        viewModelScope.launch {
            _editor.value = current.copy(saving = true)
            try {
                session.writeFile(current.path, current.draft.toByteArray(Charsets.UTF_8))
                _editor.value = null
                _messages.emit(text(R.string.saved))
                loadDirectory(_browser.value.path)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _editor.value = current.copy(saving = false)
                _messages.emit(error.friendly())
            }
        }
    }

    fun upload(uri: Uri) {
        val session = ssh ?: return
        val directory = _browser.value.path
        viewModelScope.launch {
            _browser.value = _browser.value.copy(loading = true)
            val temp = File(getApplication<Application>().cacheDir, "upload-${System.nanoTime()}")
            try {
                val name = displayName(uri)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error(text(R.string.error_generic))
                session.upload(temp, childPath(directory, name))
                _messages.emit(text(R.string.uploaded, name))
                val entries = session.list(directory)
                _browser.value = BrowserState(directory, entries, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                _browser.value = _browser.value.copy(loading = false)
                _messages.emit(failure.friendly())
            } finally {
                temp.delete()
            }
        }
    }

    fun download(entry: RemoteFile, uri: Uri) {
        val session = ssh ?: return
        viewModelScope.launch {
            _browser.value = _browser.value.copy(loading = true)
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    session.download(entry.path, output)
                } ?: error(text(R.string.error_generic))
                _messages.emit(text(R.string.downloaded, entry.name))
            } catch (error: CancellationException) {
                throw error
            } catch (_: FileTooLargeException) {
                _messages.emit(text(R.string.download_too_large))
            } catch (failure: Exception) {
                _messages.emit(failure.friendly())
            } finally {
                _browser.value = _browser.value.copy(loading = false)
            }
        }
    }

    fun delete(entry: RemoteFile) {
        val session = ssh ?: return
        viewModelScope.launch {
            _browser.value = _browser.value.copy(loading = true)
            try {
                session.delete(entry.path, entry.directory)
                _messages.emit(text(R.string.deleted))
                val directory = _browser.value.path
                val entries = session.list(directory)
                _browser.value = BrowserState(directory, entries, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                _browser.value = _browser.value.copy(loading = false)
                val message = if (entry.directory) {
                    text(R.string.dir_not_empty)
                } else {
                    failure.friendly()
                }
                _messages.emit(message)
            }
        }
    }

    fun disconnect() {
        trustGate?.complete(false)
        trustGate = null
        connectJob?.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        ssh?.close()
        ssh = null
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    private fun connect(forgetHostKey: Boolean) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            disconnectQuiet()
            _phase.value = SessionPhase.Connecting
            _pendingFingerprint.value = null
            _status.value = null
            _dropped.value = false
            val connection = repository.get(connectionId)
            if (connection == null) {
                _phase.value = SessionPhase.Failed(
                    message = text(R.string.error_missing_connection),
                    canRetry = false,
                    canForgetHostKey = false,
                )
                return@launch
            }
            _title.value = connection.name
            if (forgetHostKey) repository.clearHostKey(connectionId)
            val known = if (forgetHostKey) null else connection.hostKeyFingerprint
            val password = try {
                repository.decryptPassword(connection).toCharArray()
            } catch (_: Exception) {
                _phase.value = SessionPhase.Failed(
                    message = text(R.string.error_password_read),
                    canRetry = false,
                    canForgetHostKey = false,
                )
                return@launch
            }
            val session = SshSession()
            ssh = session
            try {
                val trusted = withContext(Dispatchers.IO) {
                    session.connectTransport(
                        host = connection.host,
                        port = connection.port,
                        knownFingerprint = known,
                        promptTrust = ::askTrust,
                    )
                }
                if (trusted != null) repository.trustHostKey(connectionId, trusted)
                withContext(Dispatchers.IO) {
                    try {
                        session.authenticate(connection.username, password)
                    } finally {
                        password.fill('\u0000')
                    }
                }
                _phase.value = SessionPhase.Connected
                startReaders(session)
                loadDirectory(session.home)
            } catch (error: CancellationException) {
                password.fill('\u0000')
                session.close()
                if (ssh === session) ssh = null
                throw error
            } catch (error: Exception) {
                password.fill('\u0000')
                session.close()
                if (ssh === session) ssh = null
                _phase.value = when (session.hostKeyFailure) {
                    HostKeyFailure.Rejected -> SessionPhase.Failed(
                        message = text(R.string.host_key_rejected),
                        canRetry = true,
                        canForgetHostKey = false,
                    )
                    HostKeyFailure.Changed -> SessionPhase.Failed(
                        message = text(R.string.host_key_changed),
                        canRetry = false,
                        canForgetHostKey = true,
                    )
                    null -> SessionPhase.Failed(
                        message = error.friendly(),
                        canRetry = true,
                        canForgetHostKey = false,
                    )
                }
            }
        }
    }

    private fun disconnectQuiet() {
        trustGate?.complete(false)
        trustGate = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        ssh?.close()
        ssh = null
    }

    private fun askTrust(fingerprint: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        trustGate = deferred
        viewModelScope.launch { _pendingFingerprint.value = fingerprint }
        return runBlocking { deferred.await() }
    }

    private fun startReaders(session: SshSession) {
        val stdout = Utf8Decoder()
        val stderr = Utf8Decoder()
        jobs += viewModelScope.launch(Dispatchers.IO) {
            try {
                session.pumpStdout { bytes -> append(bytes, stdout) }
                markShellClosed(session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                markShellClosed(session)
            }
        }
        jobs += viewModelScope.launch(Dispatchers.IO) {
            try {
                session.pumpStderr { bytes -> append(bytes, stderr) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    private fun markShellClosed(session: SshSession) {
        if (ssh !== session) return
        _dropped.value = true
        _status.value = text(R.string.shell_closed)
    }

    private fun append(bytes: ByteArray, decoder: Utf8Decoder) {
        val text = decoder.decode(bytes)
        if (text.isEmpty()) return
        buffer.write(text)
        _terminal.value = buffer.snapshot()
    }

    private fun loadDirectory(path: String) {
        val session = ssh ?: return
        viewModelScope.launch {
            _browser.value = _browser.value.copy(path = path, loading = true)
            try {
                val entries = session.list(path)
                _browser.value = BrowserState(path, entries, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not list $path", error)
                _browser.value = _browser.value.copy(loading = false)
                _messages.emit(error.friendly())
            }
        }
    }

    private suspend fun openEditor(session: SshSession, entry: RemoteFile) {
        if (entry.size > MAX_EDIT_BYTES) {
            _messages.emit(text(R.string.file_too_large))
            return
        }
        try {
            val bytes = session.readFile(entry.path, MAX_EDIT_BYTES)
            if (!looksLikeText(bytes)) {
                _messages.emit(text(R.string.file_not_text))
                return
            }
            val content = bytes.toString(Charsets.UTF_8)
            _editor.value = EditorState(entry.path, entry.name, content, content)
        } catch (_: FileTooLargeException) {
            _messages.emit(text(R.string.file_too_large))
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) {
                        return name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "upload" }
                    }
                }
            }
        }
        return "upload"
    }

    private fun text(id: Int, vararg args: Any): String {
        return getApplication<Application>().getString(id, *args)
    }

    private fun Throwable.friendly(): String {
        generateSequence(this) { it.cause }.forEach { error ->
            when (error) {
                is UnknownHostException -> return text(R.string.error_resolve_host)
                is SocketTimeoutException -> return text(R.string.error_timeout)
                is ConnectException -> return text(R.string.error_connect)
                is UserAuthException -> return text(R.string.error_auth)
            }
        }
        val reported = generateSequence(this) { it.cause }
            .mapNotNull { error -> error.message?.takeIf { it.isNotBlank() } }
            .firstOrNull()
        return reported ?: text(R.string.error_generic)
    }

    companion object {
        private const val TAG = "CabalSSH"
        fun factory(
            application: Application,
            repository: ConnectionRepository,
            connectionId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(application, repository, connectionId) }
        }
    }
}

fun parentPath(path: String): String? {
    if (path.isBlank() || path == "/") return null
    val trimmed = path.trimEnd('/')
    val slash = trimmed.lastIndexOf('/')
    if (slash < 0) return "/"
    if (slash == 0) return "/"
    return trimmed.substring(0, slash)
}

fun childPath(directory: String, name: String): String {
    val safe = name.replace("/", "").replace("\\", "")
    return if (directory.endsWith("/")) directory + safe else "$directory/$safe"
}

fun looksLikeText(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return true
    if (bytes.any { it == 0.toByte() }) return false
    return try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }
}
