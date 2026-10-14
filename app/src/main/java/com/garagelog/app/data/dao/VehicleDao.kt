package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    /** One row by id, tombstone or not (callers that want a live row check `deleted`). */
    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: String): VehicleEntity?

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

    // The odometer only moves forward: the check and the write are one statement, so two saves
    // racing can't lower it.
    @Query("UPDATE vehicles SET miles = :miles, milesDate = :milesDate, updatedAt = :updatedAt WHERE id = :id AND (miles IS NULL OR miles < :miles)")
    suspend fun bumpMileageIfHigher(id: String, miles: Int, milesDate: String, updatedAt: Long)
}
