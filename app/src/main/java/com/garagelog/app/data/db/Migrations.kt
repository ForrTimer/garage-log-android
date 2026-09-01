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

/**
 * Adds severe-duty condition flags to vehicles, bucket criteria to build phases, the new
 * build_steps checklist table, and the single-row notification_prefs settings table.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val severeDutyColumns = listOf(
            "severeDustyAreas", "severeTowing", "severeExtendedIdling", "severeLowSpeedColdWeather",
            "severeHeavyCityTrafficHot", "severeMountainousHot", "severeFrequentTowing", "severeDeepWater",
        )
        for (column in severeDutyColumns) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
        }

        db.execSQL("ALTER TABLE build_phases ADD COLUMN priorityFilter TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE build_phases ADD COLUMN budgetCap REAL DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS build_steps (
                id TEXT NOT NULL PRIMARY KEY,
                vehicleId TEXT NOT NULL,
                phaseId TEXT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL,
                priority TEXT NOT NULL,
                status TEXT NOT NULL,
                estimatedCost REAL,
                actualCost REAL,
                `order` INTEGER NOT NULL,
                manualPhaseOverride INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_prefs (
                id TEXT NOT NULL PRIMARY KEY,
                enabled INTEGER NOT NULL DEFAULT 0,
                cadence TEXT NOT NULL DEFAULT 'Weekly',
                hour INTEGER NOT NULL DEFAULT 9,
                minute INTEGER NOT NULL DEFAULT 0,
                dayOfWeek INTEGER NOT NULL DEFAULT 2,
                dayOfMonth INTEGER NOT NULL DEFAULT 1,
                month INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}
