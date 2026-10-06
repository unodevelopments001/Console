package com.unodevelopments.cblsshmngr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class SshConnection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val passwordCipher: String,
    val hostKeyFingerprint: String? = null,
)
