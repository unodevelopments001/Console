package com.unodevelopments.cblsshmngr.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import net.schmizz.sshj.xfer.InMemoryDestFile
import net.schmizz.sshj.xfer.InMemorySourceFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024

class SshSession {
    private val closed = AtomicBoolean(false)
    private val sftpMutex = Mutex()
    private var client: SSHClient? = null
    private var pty: Session? = null
    private var shell: Session.Shell? = null
    private var sftp: SFTPClient? = null

    var home: String = "."
        private set

    var hostKeyFailure: HostKeyFailure? = null
        private set

    /**
     * Opens the TCP connection and checks the host key. Returns a fingerprint when the user
     * trusted this server for the first time.
     */
    fun connectTransport(
        host: String,
        port: Int,
        knownFingerprint: String?,
        promptTrust: (String) -> Boolean,
    ): String? {
        CryptoSetup.install()
        hostKeyFailure = null
        var acceptedFingerprint: String? = null
        val ssh = SSHClient()
        client = ssh
        ssh.connectTimeout = 20_000
        ssh.timeout = 0
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, remotePort: Int, key: PublicKey): Boolean {
                val fingerprint = Fingerprints.sha256(key)
                if (knownFingerprint == null) {
                    val trusted = promptTrust(fingerprint)
                    if (!trusted) {
                        hostKeyFailure = HostKeyFailure.Rejected
                        return false
                    }
                    acceptedFingerprint = fingerprint
                    return true
                }
                if (knownFingerprint != fingerprint) {
                    hostKeyFailure = HostKeyFailure.Changed
                    return false
                }
                return true
            }

            override fun findExistingAlgorithms(hostname: String, remotePort: Int): List<String> {
                return emptyList()
            }
        })
        try {
            ssh.connect(host, port)
        } catch (error: Exception) {
            close()
            throw error
        }
        return acceptedFingerprint
    }

    fun authenticate(username: String, password: CharArray) {
        val ssh = client ?: error("Not connected")
        try {
            ssh.authPassword(username, password)
            val session = ssh.startSession()
            session.allocatePTY(
                "xterm",
                80,
                32,
                0,
                0,
                mapOf(
                    PTYMode.ECHO to 1,
                    PTYMode.ICANON to 1,
                    PTYMode.ISIG to 1,
                    PTYMode.ECHOE to 1,
                    PTYMode.ECHOCTL to 1,
                    PTYMode.OPOST to 1,
                    PTYMode.ONLCR to 1,
                    PTYMode.ICRNL to 1,
                ),
            )
            val started = session.startShell()
            val files = ssh.newSFTPClient()
            pty = session
            shell = started
            sftp = files
            home = files.canonicalize(".")
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun send(text: String) {
        val output = shell?.outputStream ?: return
        synchronized(output) {
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun resize(cols: Int, rows: Int) {
        try {
            shell?.changeWindowDimensions(cols.coerceIn(20, 400), rows.coerceIn(8, 200), 0, 0)
        } catch (_: Exception) {
        }
    }

    fun pumpStdout(onBytes: (ByteArray) -> Unit) {
        pump(shell?.inputStream, onBytes)
    }

    fun pumpStderr(onBytes: (ByteArray) -> Unit) {
        pump(shell?.errorStream, onBytes)
    }

    suspend fun list(path: String): List<RemoteFile> = withSftp { files ->
        files.ls(path)
            .map { info -> info.toRemoteFile() }
            .filter { it.name != "." && it.name != ".." }
            .sortedWith(compareBy<RemoteFile> { !it.directory }.thenBy { it.name.lowercase() })
    }

    suspend fun isDirectory(path: String): Boolean = withSftp { files ->
        files.stat(path).mode?.type == FileMode.Type.DIRECTORY
    }

    suspend fun readFile(path: String, maxBytes: Int): ByteArray = withSftp { files ->
        val remoteSize = files.size(path)
        if (remoteSize > maxBytes) throw FileTooLargeException()
        val capped = CappedOutputStream(maxBytes)
        files.get(path, capped)
        if (capped.overflow) throw FileTooLargeException()
        capped.toByteArray()
    }

    suspend fun writeFile(path: String, bytes: ByteArray) = withSftp { files ->
        val name = path.substringAfterLast('/').ifEmpty { "file" }
        files.put(BytesSource(name, bytes), path)
    }

    suspend fun upload(file: File, remotePath: String) = withSftp { files ->
        files.put(FileSystemFile(file), remotePath)
    }

    suspend fun download(path: String, output: OutputStream) = withSftp { files ->
        val remoteSize = files.size(path)
        if (remoteSize > MAX_DOWNLOAD_BYTES) throw FileTooLargeException()
        files.get(path, StreamDest(LimitedOutputStream(output, MAX_DOWNLOAD_BYTES)))
    }

    suspend fun delete(path: String, directory: Boolean) = withSftp { files ->
        if (directory) files.rmdir(path) else files.rm(path)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            shell?.close()
        } catch (_: Exception) {
        }
        try {
            pty?.close()
        } catch (_: Exception) {
        }
        try {
            sftp?.close()
        } catch (_: Exception) {
        }
        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
        shell = null
        pty = null
        sftp = null
        client = null
    }

    private suspend fun <T> withSftp(block: (SFTPClient) -> T): T {
        val files = sftp ?: error("Not connected")
        return sftpMutex.withLock {
            withContext(Dispatchers.IO) { block(files) }
        }
    }

    private fun pump(input: InputStream?, onBytes: (ByteArray) -> Unit) {
        if (input == null) return
        val buffer = ByteArray(4096)
        while (!Thread.currentThread().isInterrupted) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) onBytes(buffer.copyOf(count))
        }
    }
}

private fun net.schmizz.sshj.sftp.RemoteResourceInfo.toRemoteFile(): RemoteFile {
    val type = attributes.mode?.type
    return RemoteFile(
        name = name,
        path = path,
        directory = type == FileMode.Type.DIRECTORY,
        size = attributes.size,
    )
}

private class BytesSource(
    private val filename: String,
    private val data: ByteArray,
) : InMemorySourceFile() {
    override fun getName(): String = filename

    override fun getLength(): Long = data.size.toLong()

    override fun getInputStream(): InputStream = ByteArrayInputStream(data)
}

private class CappedOutputStream(private val maxBytes: Int) : InMemoryDestFile() {
    private val buffer = java.io.ByteArrayOutputStream()
    var overflow: Boolean = false
        private set

    fun toByteArray(): ByteArray = buffer.toByteArray()

    override fun getLength(): Long = 0

    override fun getOutputStream(): OutputStream = capping()

    override fun getOutputStream(append: Boolean): OutputStream = capping()

    private fun capping(): OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (buffer.size() >= maxBytes) {
                overflow = true
                return
            }
            buffer.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val room = maxBytes - buffer.size()
            if (room <= 0) {
                overflow = true
                return
            }
            val count = minOf(len, room)
            buffer.write(b, off, count)
            if (count < len) overflow = true
        }
    }
}

private class StreamDest(private val output: OutputStream) : InMemoryDestFile() {
    override fun getLength(): Long = 0

    override fun getOutputStream(): OutputStream = output

    override fun getOutputStream(append: Boolean): OutputStream = output
}

private class LimitedOutputStream(
    private val output: OutputStream,
    private val maxBytes: Long,
) : OutputStream() {
    private var written = 0L

    override fun write(b: Int) {
        ensure(1)
        output.write(b)
        written += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(len.toLong())
        output.write(b, off, len)
        written += len
    }

    private fun ensure(count: Long) {
        if (written + count > maxBytes) throw FileTooLargeException()
    }
}
