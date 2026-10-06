package com.unodevelopments.cblsshmngr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SqlConnectionDao {
    @Query("SELECT * FROM sql_connections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SqlConnection>>

    @Query("SELECT * FROM sql_connections WHERE id = :id")
    suspend fun get(id: Long): SqlConnection?

    @Insert
    suspend fun insert(connection: SqlConnection): Long

    @Update
    suspend fun update(connection: SqlConnection)

    @Query("DELETE FROM sql_connections WHERE id = :id")
    suspend fun delete(id: Long)
}
