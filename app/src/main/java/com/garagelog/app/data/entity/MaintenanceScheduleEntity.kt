package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenance_schedules")
data class MaintenanceScheduleEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val taskName: String,
    val intervalMiles: Int?,
    val intervalMonths: Int?,
    val lastDoneMileage: Int?,
    val lastDoneDate: String?,
)
