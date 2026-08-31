package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.MaintenanceScheduleDao
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeAll(): Flow<List<MaintenanceScheduleEntity>>
    suspend fun getAll(): List<MaintenanceScheduleEntity>
    suspend fun count(): Int
    suspend fun upsert(schedule: MaintenanceScheduleEntity)
    suspend fun delete(id: String)
    suspend fun deleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomScheduleRepository(private val dao: MaintenanceScheduleDao) : ScheduleRepository {
    override fun observeAll(): Flow<List<MaintenanceScheduleEntity>> = dao.observeAll()
    override suspend fun getAll(): List<MaintenanceScheduleEntity> = dao.getAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(schedule: MaintenanceScheduleEntity) = dao.upsert(schedule)
    override suspend fun delete(id: String) = dao.deleteById(id)
    override suspend fun deleteForVehicle(vehicleId: String) = dao.deleteForVehicle(vehicleId)
    override suspend fun deleteAll() = dao.deleteAll()
}
