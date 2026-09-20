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

/** Adds an optional cover-photo path to vehicles, shown on the Dashboard tile. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vehicles ADD COLUMN photoPath TEXT DEFAULT NULL")
    }
}

/** Links a Routine log entry to the maintenance schedule item it fulfills, so saving it can reset that item's due mileage/date. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE log_entries ADD COLUMN fulfillsScheduleId TEXT DEFAULT NULL")
    }
}

/**
 * Adds Fuel/Mileage log-entry support (gallons + full-tank flag, for the Trends tab's MPG chart)
 * and drops the Build tab entirely — retired in favor of that same Trends tab, which absorbs
 * cost-over-mileage in its place. Build data isn't migrated anywhere; it's just gone.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE log_entries ADD COLUMN gallons REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE log_entries ADD COLUMN fullTank INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP TABLE IF EXISTS build_phases")
        db.execSQL("DROP TABLE IF EXISTS build_steps")
        db.execSQL("DELETE FROM photos WHERE ownerType = 'BUILD_STEP'")
    }
}

/**
 * Adds local storage for the Claude AI features: one saved diagnosis per issue, and the
 * ask-anything chat history per vehicle. Purely additive — no existing table is touched.
 *
 * Neither table carries updatedAt/deleted because neither is synced to Drive or included in the
 * JSON backup: both are regenerable from data that already syncs, and chat history in particular
 * would bloat every snapshot for little benefit.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_diagnoses (
                issueId TEXT NOT NULL PRIMARY KEY,
                vehicleId TEXT NOT NULL,
                content TEXT NOT NULL,
                sourcesJson TEXT NOT NULL,
                model TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                milesAtRun INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                vehicleId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                sourcesJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/**
 * Lets a chat message belong to one issue's follow-up conversation rather than the vehicle-wide
 * thread. Null (every existing row) keeps its current meaning: a general question about the vehicle.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_chat_messages ADD COLUMN issueId TEXT DEFAULT NULL")
    }
}
