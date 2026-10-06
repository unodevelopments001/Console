package com.unodevelopments.cblsshmngr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "query_history")
data class QueryHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sqlConnectionId: Long,
    val sql: String,
    val createdAt: Long,
)
