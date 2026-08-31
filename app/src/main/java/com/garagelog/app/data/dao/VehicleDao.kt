package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY sortOrder ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()

    @Query("UPDATE vehicles SET miles = :miles, milesDate = :milesDate WHERE id = :id")
    suspend fun bumpMileage(id: String, miles: Int, milesDate: String)
}
