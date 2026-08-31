package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles")
    suspend fun getAllForSync(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicles WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()

    @Query("UPDATE vehicles SET miles = :miles, milesDate = :milesDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun bumpMileage(id: String, miles: Int, milesDate: String, updatedAt: Long)
}
