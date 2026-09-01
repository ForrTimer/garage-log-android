package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.BuildStepDao
import com.garagelog.app.data.entity.BuildStepEntity
import kotlinx.coroutines.flow.Flow

interface BuildStepRepository {
    fun observeAll(): Flow<List<BuildStepEntity>>
    suspend fun getAll(): List<BuildStepEntity>
    suspend fun getAllForSync(): List<BuildStepEntity>
    suspend fun count(): Int
    suspend fun upsert(step: BuildStepEntity)
    suspend fun softDelete(id: String)
    suspend fun softDeleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomBuildStepRepository(private val dao: BuildStepDao) : BuildStepRepository {
    override fun observeAll(): Flow<List<BuildStepEntity>> = dao.observeAll()
    override suspend fun getAll(): List<BuildStepEntity> = dao.getAll()
    override suspend fun getAllForSync(): List<BuildStepEntity> = dao.getAllForSync()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(step: BuildStepEntity) = dao.upsert(step)
    override suspend fun softDelete(id: String) = dao.softDeleteById(id, System.currentTimeMillis())
    override suspend fun softDeleteForVehicle(vehicleId: String) = dao.softDeleteForVehicle(vehicleId, System.currentTimeMillis())
    override suspend fun deleteAll() = dao.deleteAll()
}
