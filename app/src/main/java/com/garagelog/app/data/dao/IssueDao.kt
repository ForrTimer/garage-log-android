package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues WHERE deleted = 0 ORDER BY dateOpened DESC")
    fun observeAll(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE deleted = 0 ORDER BY dateOpened DESC")
    suspend fun getAll(): List<IssueEntity>

    @Query("SELECT * FROM issues")
    suspend fun getAllForSync(): List<IssueEntity>

    @Query("SELECT COUNT(*) FROM issues WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(issue: IssueEntity)

    @Query("UPDATE issues SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: String, updatedAt: Long)

    @Query("UPDATE issues SET deleted = 1, updatedAt = :updatedAt WHERE vehicleId = :vehicleId")
    suspend fun softDeleteForVehicle(vehicleId: String, updatedAt: Long)

    @Query("DELETE FROM issues")
    suspend fun deleteAll()
}
