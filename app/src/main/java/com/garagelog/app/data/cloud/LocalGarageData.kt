package com.garagelog.app.data.cloud

import androidx.room.withTransaction
import com.garagelog.app.data.db.AppDatabase
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.seed.SampleData

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

    /**
     * "Remove sample data": the sample vehicles, their records (including any the owner added to
     * them), their photos and Bob's history about them. Also runs when this phone sets up or joins
     * a shared garage, since sample data is never part of one.
     */
    suspend fun removeSamples() {
        val sample = "'${SampleData.PREFIX}%'"
        val photoFiles = mutableListOf<String>()
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val owners = "SELECT id FROM log_entries WHERE vehicleId LIKE $sample UNION SELECT id FROM issues WHERE vehicleId LIKE $sample"
            db.query("SELECT filePath FROM photos WHERE ownerId IN ($owners)").use { cursor ->
                while (cursor.moveToNext()) photoFiles += cursor.getString(0)
            }
            db.execSQL("DELETE FROM photos WHERE ownerId IN ($owners)")
            listOf("log_entries", "issues", "maintenance_schedules", "ai_diagnoses", "ai_chat_messages").forEach {
                db.execSQL("DELETE FROM $it WHERE vehicleId LIKE $sample")
            }
            db.execSQL("DELETE FROM vehicles WHERE id LIKE $sample")
        }
        photoFiles.forEach { photoStore.delete(it) }
    }

    /**
     * Gives this person's unstamped logs and issues their stamp (createdByName), so entries made
     * before stamps existed show who added them too. Their own entries are the ones with their uid;
     * the garage owner also gets the ones with none, which are from before the garage (only the
     * owner's records were uploaded into it). Bumps updatedAt so the next push carries them.
     */
    suspend fun stampOwnEntries(uid: String, stamp: String, isOwner: Boolean) {
        if (uid.isEmpty() || stamp.isEmpty()) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            listOf("log_entries", "issues").forEach { table ->
                db.execSQL(
                    "UPDATE $table SET createdByName = ?, updatedAt = ?, " +
                        "createdBy = CASE WHEN createdBy = '' THEN ? ELSE createdBy END " +
                        "WHERE createdByName = '' AND deleted = 0 " +
                        "AND id NOT LIKE '${SampleData.PREFIX}%' AND vehicleId NOT LIKE '${SampleData.PREFIX}%' " +
                        "AND (createdBy = ? OR (? AND createdBy = ''))",
                    arrayOf<Any?>(stamp, now, uid, uid, if (isOwner) 1 else 0),
                )
            }
        }
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
