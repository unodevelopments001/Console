package com.unodevelopments.cblsshmngr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryHistoryDao {
    @Query("SELECT * FROM query_history WHERE sqlConnectionId = :connectionId ORDER BY createdAt DESC LIMIT 20")
    fun observe(connectionId: Long): Flow<List<QueryHistory>>

    @Query("SELECT * FROM query_history WHERE sqlConnectionId = :connectionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun newest(connectionId: Long): QueryHistory?

    @Query("SELECT id FROM query_history WHERE sqlConnectionId = :connectionId ORDER BY createdAt DESC")
    suspend fun ids(connectionId: Long): List<Long>

    @Insert
    suspend fun insert(item: QueryHistory)

    @Query("DELETE FROM query_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM query_history WHERE sqlConnectionId = :connectionId")
    suspend fun deleteForConnection(connectionId: Long)
}
