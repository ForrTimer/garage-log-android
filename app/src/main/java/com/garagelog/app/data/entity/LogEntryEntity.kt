package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogCategory { Routine, Repair, Upgrade, Fuel, Mileage }

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
    // category == Fuel only. gallons + cost (the pump total) drive $/gal and the Trends MPG
    // chart; fullTank gates which fill-ups are safe to diff against each other for MPG (a
    // partial fill breaks the "miles since last full tank / gallons since last full tank" math).
    val gallons: Double? = null,
    val fullTank: Boolean = false,
    // category == Fuel only: the owner skipped logging at least one fill-up before this one, so the
    // fuel burned since the previous entry is unknown and this stretch can't give an MPG figure.
    val missedFillUp: Boolean = false,
    // Firebase uid of whoever first saved this in a shared garage; "" = before shared garages, or
    // never in one. Gates a Standard member editing someone else's entry (firestore.rules).
    val createdBy: String = "",
    // Who that was, as a short stamp ("E.Forr", see util.userStamp). Saved on every new entry even
    // outside a shared garage, so it's already there if this phone joins one; shown only in one.
    val createdByName: String = "",
    // Started but not finished (parts bought, job not done yet). Its cost counts right away; a
    // linked maintenance item isn't reset until it's finished (RecordsViewModel.saveLog).
    val inProgress: Boolean = false,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
