package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDiagnosisDao {
    @Query("SELECT * FROM ai_diagnoses")
    fun observeAll(): Flow<List<AiDiagnosisEntity>>

    @Query("SELECT * FROM ai_diagnoses WHERE issueId = :issueId")
    suspend fun getForIssue(issueId: String): AiDiagnosisEntity?

    @Upsert
    suspend fun upsert(diagnosis: AiDiagnosisEntity)

    @Query("DELETE FROM ai_diagnoses WHERE issueId = :issueId")
    suspend fun deleteForIssue(issueId: String)

    @Query("DELETE FROM ai_diagnoses WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM ai_diagnoses")
    suspend fun deleteAll()
}

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC")
    suspend fun getAll(): List<AiChatMessageEntity>

    @Upsert
    suspend fun upsert(message: AiChatMessageEntity)

    // `IS` rather than `=` so it also matches the vehicle-wide thread, where issueId is null.
    @Query("DELETE FROM ai_chat_messages WHERE vehicleId = :vehicleId AND issueId IS :issueId")
    suspend fun deleteForThread(vehicleId: String, issueId: String?)

    @Query("DELETE FROM ai_chat_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ai_chat_messages WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun deleteAll()
}
