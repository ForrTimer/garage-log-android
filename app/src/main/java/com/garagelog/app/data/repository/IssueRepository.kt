package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.IssueDao
import com.garagelog.app.data.entity.IssueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

interface IssueRepository {
    fun observeAll(): Flow<List<IssueEntity>>
    suspend fun getById(id: String): IssueEntity?
    suspend fun getAll(): List<IssueEntity>
    suspend fun getAllForSync(): List<IssueEntity>
    suspend fun count(): Int
    suspend fun upsert(issue: IssueEntity)
    suspend fun softDelete(id: String)
    suspend fun softDeleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomIssueRepository(private val dao: IssueDao) : IssueRepository {
    override fun observeAll(): Flow<List<IssueEntity>> = dao.observeAll().distinctUntilChanged()
    override suspend fun getById(id: String): IssueEntity? = dao.getById(id)
    override suspend fun getAll(): List<IssueEntity> = dao.getAll()
    override suspend fun getAllForSync(): List<IssueEntity> = dao.getAllForSync()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(issue: IssueEntity) = dao.upsert(issue)
    override suspend fun softDelete(id: String) = dao.softDeleteById(id, System.currentTimeMillis())
    override suspend fun softDeleteForVehicle(vehicleId: String) = dao.softDeleteForVehicle(vehicleId, System.currentTimeMillis())
    override suspend fun deleteAll() = dao.deleteAll()
}
