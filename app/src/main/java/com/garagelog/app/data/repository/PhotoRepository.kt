package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.PhotoDao
import com.garagelog.app.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun observeAll(): Flow<List<PhotoEntity>>
    suspend fun getAll(): List<PhotoEntity>
    suspend fun getAllForSync(): List<PhotoEntity>
    fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>>
    suspend fun getForOwner(ownerType: String, ownerId: String): List<PhotoEntity>
    suspend fun upsert(photo: PhotoEntity)
    suspend fun softDelete(id: String)
    suspend fun softDeleteForOwner(ownerType: String, ownerId: String)
    suspend fun softDeleteForVehicle(vehicleId: String)
    suspend fun deleteAll()
}

class RoomPhotoRepository(private val dao: PhotoDao) : PhotoRepository {
    override fun observeAll(): Flow<List<PhotoEntity>> = dao.observeAll()
    override suspend fun getAll(): List<PhotoEntity> = dao.getAll()
    override suspend fun getAllForSync(): List<PhotoEntity> = dao.getAllForSync()
    override fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>> =
        dao.observeForOwner(ownerType, ownerId)
    override suspend fun getForOwner(ownerType: String, ownerId: String): List<PhotoEntity> =
        dao.getForOwner(ownerType, ownerId)
    override suspend fun upsert(photo: PhotoEntity) = dao.upsert(photo)
    override suspend fun softDelete(id: String) = dao.softDeleteById(id, System.currentTimeMillis())
    override suspend fun softDeleteForOwner(ownerType: String, ownerId: String) =
        dao.softDeleteForOwner(ownerType, ownerId, System.currentTimeMillis())
    override suspend fun softDeleteForVehicle(vehicleId: String) = dao.softDeleteForVehicle(vehicleId, System.currentTimeMillis())
    override suspend fun deleteAll() = dao.deleteAll()
}
