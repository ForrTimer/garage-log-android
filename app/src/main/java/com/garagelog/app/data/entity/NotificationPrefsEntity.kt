package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderCadence { Daily, Weekly, Monthly, Yearly }

/**
 * Single-row settings table (fixed [id]) for the "remind me to log mileage" notification.
 * [dayOfWeek] uses [java.util.Calendar] constants (SUNDAY=1..SATURDAY=7) and applies to Weekly;
 * [dayOfMonth] (1-28, to stay valid in every month) applies to Monthly and Yearly; [month] uses
 * Calendar constants (JANUARY=0..DECEMBER=11) and applies to Yearly only.
 */
@Entity(tableName = "notification_prefs")
data class NotificationPrefsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val enabled: Boolean = false,
    val cadence: String = ReminderCadence.Weekly.name,
    val hour: Int = 9,
    val minute: Int = 0,
    val dayOfWeek: Int = 2, // Monday
    val dayOfMonth: Int = 1,
    val month: Int = 0, // January
) {
    companion object {
        const val SINGLETON_ID = "singleton"
    }
}
