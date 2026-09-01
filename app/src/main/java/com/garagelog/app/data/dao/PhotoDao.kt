package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE deleted = 0 ORDER BY addedDate DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 0 ORDER BY addedDate DESC")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photos")
    suspend fun getAllForSync(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE deleted = 0 AND ownerType = :ownerType AND ownerId = :ownerId ORDER BY addedDate DESC")
    fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 0 AND ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun getForOwner(ownerType: String, ownerId: String): List<PhotoEntity>

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Query("UPDATE photos SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE photos SET deleted = 1, updatedAt = :updatedAt WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun softDeleteForOwner(ownerType: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE photos SET deleted = 1, updatedAt = :updatedAt WHERE " +
            "ownerId IN (SELECT id FROM log_entries WHERE vehicleId = :vehicleId) " +
            "OR ownerId IN (SELECT id FROM issues WHERE vehicleId = :vehicleId) " +
            "OR ownerId IN (SELECT id FROM build_steps WHERE vehicleId = :vehicleId)",
    )
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
