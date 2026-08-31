package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.PhotoDao
import com.garagelog.app.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun observeAll(): Flow<List<PhotoEntity>>
    suspend fun getAll(): List<PhotoEntity>
    fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>>
    suspend fun upsert(photo: PhotoEntity)
    suspend fun delete(photo: PhotoEntity)
    suspend fun deleteForOwner(ownerType: String, ownerId: String)
    suspend fun deleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomPhotoRepository(private val dao: PhotoDao) : PhotoRepository {
    override fun observeAll(): Flow<List<PhotoEntity>> = dao.observeAll()
    override suspend fun getAll(): List<PhotoEntity> = dao.getAll()
    override fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>> =
        dao.observeForOwner(ownerType, ownerId)
    override suspend fun upsert(photo: PhotoEntity) = dao.upsert(photo)
    override suspend fun delete(photo: PhotoEntity) = dao.delete(photo)
    override suspend fun deleteForOwner(ownerType: String, ownerId: String) = dao.deleteForOwner(ownerType, ownerId)
    override suspend fun deleteForVehicle(vehicleId: String) = dao.deleteForVehicle(vehicleId)
    override suspend fun deleteAll() = dao.deleteAll()
}
