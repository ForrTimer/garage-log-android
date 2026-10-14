package com.garagelog.app.data.cloud

import androidx.room.withTransaction
import com.garagelog.app.data.db.AppDatabase
import com.garagelog.app.data.photo.PhotoStore

/**
 * Bulk removals of this phone's garage records, for the two times a device's data stops being its
 * own: a Standard member joining someone else's garage, and an admin taking a vehicle away from
 * them. Hard deletes, not tombstones — these rows were never this person's to delete for everyone,
 * they just shouldn't be on this phone any more. Reminder settings are per person and stay.
 */
class LocalGarageData(private val database: AppDatabase, private val photoStore: PhotoStore) {

    /** Everything but reminder settings: vehicles, their records, photos, and Bob's history. */
    suspend fun clearAll() {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            listOf("vehicles", "log_entries", "issues", "maintenance_schedules", "photos", "ai_diagnoses", "ai_chat_messages")
                .forEach { db.execSQL("DELETE FROM $it") }
        }
        photoStore.deleteAll()
    }

    /** Drops vehicles not in [keep] and the records hanging off them. */
    suspend fun keepOnlyVehicles(keep: List<String>) {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val placeholders = keep.joinToString(",") { "?" }
            val notKept = if (keep.isEmpty()) "1" else "vehicleId NOT IN ($placeholders)"
            val args = keep.toTypedArray<Any?>()
            listOf("log_entries", "issues", "maintenance_schedules", "ai_chat_messages").forEach {
                db.execSQL("DELETE FROM $it WHERE $notKept", args)
            }
            db.execSQL("DELETE FROM vehicles WHERE ${if (keep.isEmpty()) "1" else "id NOT IN ($placeholders)"}", args)
        }
    }
}
