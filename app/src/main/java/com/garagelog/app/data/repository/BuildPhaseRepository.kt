package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.BuildPhaseDao
import com.garagelog.app.data.entity.BuildPhaseEntity
import kotlinx.coroutines.flow.Flow

interface BuildPhaseRepository {
    fun observeAll(): Flow<List<BuildPhaseEntity>>
    suspend fun getAll(): List<BuildPhaseEntity>
    suspend fun count(): Int
    suspend fun upsert(phase: BuildPhaseEntity)
    suspend fun delete(id: String)
    suspend fun deleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomBuildPhaseRepository(private val dao: BuildPhaseDao) : BuildPhaseRepository {
    override fun observeAll(): Flow<List<BuildPhaseEntity>> = dao.observeAll()
    override suspend fun getAll(): List<BuildPhaseEntity> = dao.getAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(phase: BuildPhaseEntity) = dao.upsert(phase)
    override suspend fun delete(id: String) = dao.deleteById(id)
    override suspend fun deleteForVehicle(vehicleId: String) = dao.deleteForVehicle(vehicleId)
    override suspend fun deleteAll() = dao.deleteAll()
}
