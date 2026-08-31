package com.garagelog.app.data.backup

import kotlinx.serialization.Serializable

/**
 * Mirrors the PWA's flat backup JSON shape ({vehicles, logs, issues, buildPhases})
 * so an export from the old web app can still be imported here — extended with
 * maintenanceSchedules, additively, so older exports (missing that key) still parse.
 *
 * Numeric-looking fields are kept as String? rather than Int/Double because the PWA
 * itself stores them inconsistently (raw numbers in the original seed, but strings
 * pulled straight from <input> elements after any edit) — parsing leniently at the
 * point of use avoids rejecting a real-world export over that inconsistency.
 */
@Serializable
data class BackupData(
    val vehicles: List<BackupVehicle> = emptyList(),
    val logs: List<BackupLog> = emptyList(),
    val issues: List<BackupIssue> = emptyList(),
    val buildPhases: List<BackupPhase> = emptyList(),
    val maintenanceSchedules: List<BackupSchedule> = emptyList(),
)

@Serializable
data class BackupVehicle(
    val id: String,
    val name: String = "",
    val year: String? = null,
    val make: String = "",
    val model: String = "",
    val engine: String = "",
    val drivetrain: String = "",
    val vin: String = "",
    val color: String = "",
    val miles: String? = null,
    val milesDate: String = "",
    val role: String = "",
    val notes: String = "",
)

@Serializable
data class BackupLog(
    val id: String,
    val vehicleId: String,
    val date: String = "",
    val mileage: String? = null,
    val category: String = "Routine",
    val task: String = "",
    val cost: String? = null,
    val parts: String = "",
    val notes: String = "",
)

@Serializable
data class BackupIssue(
    val id: String,
    val vehicleId: String,
    val title: String = "",
    val status: String = "Open",
    val priority: String = "Normal",
    val dateOpened: String = "",
    val dateResolved: String = "",
    val description: String = "",
)

@Serializable
data class BackupPhase(
    val id: String,
    val vehicleId: String,
    val phase: String = "",
    val status: String = "Not started",
    val order: String? = "0",
    val notes: String = "",
)

@Serializable
data class BackupSchedule(
    val id: String,
    val vehicleId: String,
    val taskName: String = "",
    val intervalMiles: String? = null,
    val intervalMonths: String? = null,
    val lastDoneMileage: String? = null,
    val lastDoneDate: String? = null,
)
