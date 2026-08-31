package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues ORDER BY dateOpened DESC")
    fun observeAll(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues ORDER BY dateOpened DESC")
    suspend fun getAll(): List<IssueEntity>

    @Query("SELECT COUNT(*) FROM issues")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(issue: IssueEntity)

    @Delete
    suspend fun delete(issue: IssueEntity)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM issues WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM issues")
    suspend fun deleteAll()
}
