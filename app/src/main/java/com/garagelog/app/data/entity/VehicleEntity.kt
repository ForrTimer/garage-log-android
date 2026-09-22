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
    val photoPath: String? = null,
    // Severe-duty conditions from typical OEM maintenance manuals — any of these set halves
    // computed maintenance intervals (see util.ScheduleStatus.computeDueInfo).
    val severeDustyAreas: Boolean = false,
    val severeTowing: Boolean = false,
    val severeExtendedIdling: Boolean = false,
    val severeLowSpeedColdWeather: Boolean = false,
    val severeHeavyCityTrafficHot: Boolean = false,
    val severeMountainousHot: Boolean = false,
    val severeFrequentTowing: Boolean = false,
    val severeDeepWater: Boolean = false,
    // Spec fields, filled from the catalog pickers or a VIN decode (see data/catalog). Plain
    // strings/numbers rather than enums: a 1994 Suburban mid engine swap doesn't have to fit a list.
    // [engine] stays the free-text engine description; these are the structured parts beside it.
    val trim: String = "",
    val bodyStyle: String = "",
    val cabStyle: String = "",
    val bedLength: String = "",
    val transmissionType: String = "",
    val transmissionSpeeds: Int? = null,
    val cylinders: Int? = null,
    val displacementL: Double? = null,
    val fuelType: String = "",
    val aspiration: String = "",
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
) {
    val isSevereDuty: Boolean
        get() = severeDustyAreas || severeTowing || severeExtendedIdling || severeLowSpeedColdWeather ||
            severeHeavyCityTrafficHot || severeMountainousHot || severeFrequentTowing || severeDeepWater
}
