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

    @Upsert
    suspend fun upsert(message: AiChatMessageEntity)

    @Query("DELETE FROM ai_chat_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ai_chat_messages WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun deleteAll()
}
