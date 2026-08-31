package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.IssueDao
import com.garagelog.app.data.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

interface IssueRepository {
    fun observeAll(): Flow<List<IssueEntity>>
    suspend fun getAll(): List<IssueEntity>
    suspend fun count(): Int
    suspend fun upsert(issue: IssueEntity)
    suspend fun delete(id: String)
    suspend fun deleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomIssueRepository(private val dao: IssueDao) : IssueRepository {
    override fun observeAll(): Flow<List<IssueEntity>> = dao.observeAll()
    override suspend fun getAll(): List<IssueEntity> = dao.getAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(issue: IssueEntity) = dao.upsert(issue)
    override suspend fun delete(id: String) = dao.deleteById(id)
    override suspend fun deleteForVehicle(vehicleId: String) = dao.deleteForVehicle(vehicleId)
    override suspend fun deleteAll() = dao.deleteAll()
}
