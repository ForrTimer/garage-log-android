package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    /** One row by id, tombstone or not (callers that want a live row check `deleted`). */
    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: String): LogEntryEntity?

    @Query("SELECT * FROM log_entries WHERE deleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE deleted = 0 ORDER BY date DESC")
    suspend fun getAll(): List<LogEntryEntity>

    // Sample data, and anything added to a sample vehicle, stays on the phone (see SampleData).
    @Query("SELECT * FROM log_entries WHERE id NOT LIKE 'sample-%' AND vehicleId NOT LIKE 'sample-%'")
    suspend fun getAllForSync(): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM log_entries WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entry: LogEntryEntity)

    @Query("UPDATE log_entries SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE log_entries SET deleted = 1, updatedAt = :updatedAt WHERE vehicleId = :vehicleId")
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}
