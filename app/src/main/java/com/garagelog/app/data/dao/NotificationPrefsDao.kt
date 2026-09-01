package com.garagelog.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garagelog.app.data.entity.NotificationPrefsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationPrefsDao {
    @Query("SELECT * FROM notification_prefs WHERE id = :id LIMIT 1")
    fun observe(id: String = NotificationPrefsEntity.SINGLETON_ID): Flow<NotificationPrefsEntity?>

    @Query("SELECT * FROM notification_prefs WHERE id = :id LIMIT 1")
    suspend fun get(id: String = NotificationPrefsEntity.SINGLETON_ID): NotificationPrefsEntity?

    @Upsert
    suspend fun upsert(prefs: NotificationPrefsEntity)
}
