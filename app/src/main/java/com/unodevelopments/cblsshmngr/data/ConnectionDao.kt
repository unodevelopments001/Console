package com.unodevelopments.cblsshmngr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SshConnection>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun get(id: Long): SshConnection?

    @Insert
    suspend fun insert(connection: SshConnection): Long

    @Update
    suspend fun update(connection: SshConnection)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE connections SET hostKeyFingerprint = :fingerprint WHERE id = :id")
    suspend fun updateFingerprint(id: Long, fingerprint: String)

    @Query("UPDATE connections SET hostKeyFingerprint = NULL WHERE id = :id")
    suspend fun clearFingerprint(id: Long)
}
