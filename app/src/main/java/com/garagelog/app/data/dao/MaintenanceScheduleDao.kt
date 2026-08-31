package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceScheduleDao {
    @Query("SELECT * FROM maintenance_schedules ORDER BY taskName ASC")
    fun observeAll(): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules ORDER BY taskName ASC")
    suspend fun getAll(): List<MaintenanceScheduleEntity>

    @Query("SELECT COUNT(*) FROM maintenance_schedules")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(schedule: MaintenanceScheduleEntity)

    @Delete
    suspend fun delete(schedule: MaintenanceScheduleEntity)

    @Query("DELETE FROM maintenance_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM maintenance_schedules WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM maintenance_schedules")
    suspend fun deleteAll()
}
