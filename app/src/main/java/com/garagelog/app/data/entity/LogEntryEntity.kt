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
    // Set only when category == Routine and the user picked which scheduled maintenance item
    // this entry fulfills — lets saveLog() reset that schedule's lastDoneMileage/lastDoneDate
    // instead of leaving it to go stale. Null means "not linked" (older entries, non-routine
    // categories, or the user left it unset), never an error.
    val fulfillsScheduleId: String? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
