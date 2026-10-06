package com.unodevelopments.cblsshmngr.ssh

data class RemoteFile(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
)
