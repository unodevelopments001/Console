package com.unodevelopments.cblsshmngr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_commands")
data class SavedCommand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sshConnectionId: Long,
    val label: String,
    val command: String,
)
