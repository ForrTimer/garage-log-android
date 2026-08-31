package com.garagelog.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds sync bookkeeping (updatedAt/deleted on every table, driveFileId on photos) without
 * touching existing data — the phone this shipped to already has real seed edits on it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val syncedTables = listOf("vehicles", "log_entries", "issues", "build_phases", "maintenance_schedules", "photos")
        for (table in syncedTables) {
            db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        }
        db.execSQL("ALTER TABLE photos ADD COLUMN driveFileId TEXT DEFAULT NULL")
    }
}
