package com.unodevelopments.cblsshmngr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCommandDao {
    @Query("SELECT * FROM saved_commands WHERE sshConnectionId = :sshId ORDER BY label COLLATE NOCASE")
    fun observe(sshId: Long): Flow<List<SavedCommand>>

    @Insert
    suspend fun insert(command: SavedCommand)

    @Query("DELETE FROM saved_commands WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_commands WHERE sshConnectionId = :sshId")
    suspend fun deleteForConnection(sshId: Long)
}
