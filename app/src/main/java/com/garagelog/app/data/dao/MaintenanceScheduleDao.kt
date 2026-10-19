package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceScheduleDao {
    /** One row by id, tombstone or not (callers that want a live row check `deleted`). */
    @Query("SELECT * FROM maintenance_schedules WHERE id = :id")
    suspend fun getById(id: String): MaintenanceScheduleEntity?

    @Query("SELECT * FROM maintenance_schedules WHERE deleted = 0 ORDER BY taskName ASC")
    fun observeAll(): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules WHERE deleted = 0 ORDER BY taskName ASC")
    suspend fun getAll(): List<MaintenanceScheduleEntity>

    // Sample data, and anything added to a sample vehicle, stays on the phone (see SampleData).
    @Query("SELECT * FROM maintenance_schedules WHERE id NOT LIKE 'sample-%' AND vehicleId NOT LIKE 'sample-%'")
    suspend fun getAllForSync(): List<MaintenanceScheduleEntity>

    @Query("SELECT COUNT(*) FROM maintenance_schedules WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(schedule: MaintenanceScheduleEntity)

    @Query("UPDATE maintenance_schedules SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE maintenance_schedules SET deleted = 1, updatedAt = :updatedAt WHERE vehicleId = :vehicleId")
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM maintenance_schedules")
    suspend fun deleteAll()
}
