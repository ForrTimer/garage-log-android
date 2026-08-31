package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.BuildPhaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildPhaseDao {
    @Query("SELECT * FROM build_phases ORDER BY `order` ASC")
    fun observeAll(): Flow<List<BuildPhaseEntity>>

    @Query("SELECT * FROM build_phases ORDER BY `order` ASC")
    suspend fun getAll(): List<BuildPhaseEntity>

    @Query("SELECT COUNT(*) FROM build_phases")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(phase: BuildPhaseEntity)

    @Delete
    suspend fun delete(phase: BuildPhaseEntity)

    @Query("DELETE FROM build_phases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM build_phases WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM build_phases")
    suspend fun deleteAll()
}
