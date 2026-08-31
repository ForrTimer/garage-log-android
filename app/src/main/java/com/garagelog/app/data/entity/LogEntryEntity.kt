package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogCategory { Routine, Repair, Upgrade, Diagnostic }

@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val date: String,
    val mileage: Int?,
    val category: String,
    val task: String,
    val cost: Double?,
    val parts: String,
    val notes: String,
)
