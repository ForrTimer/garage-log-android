package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.LogEntryDao
import com.garagelog.app.data.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun observeAll(): Flow<List<LogEntryEntity>>
    suspend fun getAll(): List<LogEntryEntity>
    suspend fun count(): Int
    suspend fun upsert(entry: LogEntryEntity)
    suspend fun delete(id: String)
    suspend fun deleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomLogRepository(private val dao: LogEntryDao) : LogRepository {
    override fun observeAll(): Flow<List<LogEntryEntity>> = dao.observeAll()
    override suspend fun getAll(): List<LogEntryEntity> = dao.getAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(entry: LogEntryEntity) = dao.upsert(entry)
    override suspend fun delete(id: String) = dao.deleteById(id)
    override suspend fun deleteForVehicle(vehicleId: String) = dao.deleteForVehicle(vehicleId)
    override suspend fun deleteAll() = dao.deleteAll()
}
