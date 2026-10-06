package com.unodevelopments.cblsshmngr.ssh

import java.io.IOException

class FileTooLargeException : IOException("File is too large to edit")

sealed interface HostKeyFailure {
    data object Rejected : HostKeyFailure
    data object Changed : HostKeyFailure
}
