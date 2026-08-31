package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PhotoOwnerType { LOG, ISSUE }

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val filePath: String,
    val addedDate: String,
    val driveFileId: String? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
