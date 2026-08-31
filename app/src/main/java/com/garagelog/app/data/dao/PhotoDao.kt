package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY addedDate DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY addedDate DESC")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY addedDate DESC")
    fun observeForOwner(ownerType: String, ownerId: String): Flow<List<PhotoEntity>>

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: String)

    @Query("DELETE FROM photos WHERE ownerId IN (SELECT id FROM log_entries WHERE vehicleId = :vehicleId) OR ownerId IN (SELECT id FROM issues WHERE vehicleId = :vehicleId)")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
