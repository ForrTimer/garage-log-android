package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.NotificationPrefsDao
import com.garagelog.app.data.entity.NotificationPrefsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface NotificationPrefsRepository {
    fun observe(): Flow<NotificationPrefsEntity>
    suspend fun get(): NotificationPrefsEntity
    suspend fun upsert(prefs: NotificationPrefsEntity)
}

class RoomNotificationPrefsRepository(private val dao: NotificationPrefsDao) : NotificationPrefsRepository {
    override fun observe(): Flow<NotificationPrefsEntity> = dao.observe().map { it ?: NotificationPrefsEntity() }
    override suspend fun get(): NotificationPrefsEntity = dao.get() ?: NotificationPrefsEntity()
    override suspend fun upsert(prefs: NotificationPrefsEntity) = dao.upsert(prefs)
}
