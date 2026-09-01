package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.BuildStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildStepDao {
    @Query("SELECT * FROM build_steps WHERE deleted = 0 ORDER BY `order` ASC")
    fun observeAll(): Flow<List<BuildStepEntity>>

    @Query("SELECT * FROM build_steps WHERE deleted = 0 ORDER BY `order` ASC")
    suspend fun getAll(): List<BuildStepEntity>

    @Query("SELECT * FROM build_steps")
    suspend fun getAllForSync(): List<BuildStepEntity>

    @Query("SELECT COUNT(*) FROM build_steps WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(step: BuildStepEntity)

    @Query("UPDATE build_steps SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE build_steps SET deleted = 1, updatedAt = :updatedAt WHERE vehicleId = :vehicleId")
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM build_steps")
    suspend fun deleteAll()
}
