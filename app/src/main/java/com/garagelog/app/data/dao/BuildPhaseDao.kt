package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.BuildPhaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildPhaseDao {
    @Query("SELECT * FROM build_phases WHERE deleted = 0 ORDER BY `order` ASC")
    fun observeAll(): Flow<List<BuildPhaseEntity>>

    @Query("SELECT * FROM build_phases WHERE deleted = 0 ORDER BY `order` ASC")
    suspend fun getAll(): List<BuildPhaseEntity>

    @Query("SELECT * FROM build_phases")
    suspend fun getAllForSync(): List<BuildPhaseEntity>

    @Query("SELECT COUNT(*) FROM build_phases WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(phase: BuildPhaseEntity)

    @Query("UPDATE build_phases SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE build_phases SET deleted = 1, updatedAt = :updatedAt WHERE vehicleId = :vehicleId")
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM build_phases")
    suspend fun deleteAll()
}
