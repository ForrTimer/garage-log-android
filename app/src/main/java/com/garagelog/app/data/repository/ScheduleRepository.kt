package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.MaintenanceScheduleDao
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeAll(): Flow<List<MaintenanceScheduleEntity>>
    suspend fun getAll(): List<MaintenanceScheduleEntity>
    suspend fun getAllForSync(): List<MaintenanceScheduleEntity>
    suspend fun count(): Int
    suspend fun upsert(schedule: MaintenanceScheduleEntity)
    suspend fun softDelete(id: String)
    suspend fun softDeleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomScheduleRepository(private val dao: MaintenanceScheduleDao) : ScheduleRepository {
    override fun observeAll(): Flow<List<MaintenanceScheduleEntity>> = dao.observeAll()
    override suspend fun getAll(): List<MaintenanceScheduleEntity> = dao.getAll()
    override suspend fun getAllForSync(): List<MaintenanceScheduleEntity> = dao.getAllForSync()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(schedule: MaintenanceScheduleEntity) = dao.upsert(schedule)
    override suspend fun softDelete(id: String) = dao.softDeleteById(id, System.currentTimeMillis())
    override suspend fun softDeleteForVehicle(vehicleId: String) = dao.softDeleteForVehicle(vehicleId, System.currentTimeMillis())
    override suspend fun deleteAll() = dao.deleteAll()
}
