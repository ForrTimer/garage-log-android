package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val year: Int?,
    val make: String,
    val model: String,
    val engine: String,
    val drivetrain: String,
    val vin: String,
    val color: String,
    val miles: Int?,
    val milesDate: String,
    val role: String,
    val notes: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
