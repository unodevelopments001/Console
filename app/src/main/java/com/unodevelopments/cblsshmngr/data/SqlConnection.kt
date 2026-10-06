package com.unodevelopments.cblsshmngr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sql_connections")
data class SqlConnection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sshConnectionId: Long,
    val sqlHost: String,
    val sqlPort: Int,
    val username: String,
    val passwordCipher: String,
    val databaseName: String,
)
