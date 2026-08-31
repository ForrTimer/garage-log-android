package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries ORDER BY date DESC")
    suspend fun getAll(): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entry: LogEntryEntity)

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM log_entries WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}
