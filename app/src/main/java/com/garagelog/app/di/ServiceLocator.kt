package com.garagelog.app.di

import android.content.Context
import androidx.room.Room
import com.garagelog.app.data.auth.AuthManager
import com.garagelog.app.data.backup.BackupManager
import com.garagelog.app.data.db.AppDatabase
import com.garagelog.app.data.db.MIGRATION_1_2
import com.garagelog.app.data.db.MIGRATION_2_3
import com.garagelog.app.data.db.MIGRATION_3_4
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.repository.BuildPhaseRepository
import com.garagelog.app.data.repository.BuildStepRepository
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.NotificationPrefsRepository
import com.garagelog.app.data.repository.PhotoRepository
import com.garagelog.app.data.repository.RoomBuildPhaseRepository
import com.garagelog.app.data.repository.RoomBuildStepRepository
import com.garagelog.app.data.repository.RoomIssueRepository
import com.garagelog.app.data.repository.RoomLogRepository
import com.garagelog.app.data.repository.RoomNotificationPrefsRepository
import com.garagelog.app.data.repository.RoomPhotoRepository
import com.garagelog.app.data.repository.RoomScheduleRepository
import com.garagelog.app.data.repository.RoomVehicleRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.data.seed.SeedData
import com.garagelog.app.data.sync.DriveApiClient
import com.garagelog.app.data.sync.SyncRepository
import com.garagelog.app.data.sync.SyncStatusHolder
import com.garagelog.app.data.sync.SyncWorker

/**
 * Hand-rolled composition root — no DI framework needed for a single-user local app.
 * Everything downstream depends on the repository interfaces, not on Room directly,
 * so a future sync-aware repository can be substituted here without touching UI code.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    val vehicleRepository: VehicleRepository = RoomVehicleRepository(database.vehicleDao())
    val logRepository: LogRepository = RoomLogRepository(database.logEntryDao())
    val issueRepository: IssueRepository = RoomIssueRepository(database.issueDao())
    val buildPhaseRepository: BuildPhaseRepository = RoomBuildPhaseRepository(database.buildPhaseDao())
    val buildStepRepository: BuildStepRepository = RoomBuildStepRepository(database.buildStepDao())
    val scheduleRepository: ScheduleRepository = RoomScheduleRepository(database.maintenanceScheduleDao())
    val photoRepository: PhotoRepository = RoomPhotoRepository(database.photoDao())
    val notificationPrefsRepository: NotificationPrefsRepository = RoomNotificationPrefsRepository(database.notificationPrefsDao())

    val photoStore = PhotoStore(appContext)

    val backupManager = BackupManager(
        vehicleRepository = vehicleRepository,
        logRepository = logRepository,
        issueRepository = issueRepository,
        buildPhaseRepository = buildPhaseRepository,
        buildStepRepository = buildStepRepository,
        scheduleRepository = scheduleRepository,
        photoRepository = photoRepository,
        photoStore = photoStore,
    )

    val authManager = AuthManager(appContext)
    private val driveApiClient = DriveApiClient()
    val syncStatusHolder = SyncStatusHolder()

    val syncRepository = SyncRepository(
        authManager = authManager,
        driveApi = driveApiClient,
        vehicleRepository = vehicleRepository,
        logRepository = logRepository,
        issueRepository = issueRepository,
        buildPhaseRepository = buildPhaseRepository,
        scheduleRepository = scheduleRepository,
        photoRepository = photoRepository,
        photoStore = photoStore,
        statusHolder = syncStatusHolder,
    )

    /** Fire-and-forget debounced sync request — safe to call after every mutation, signed in or not. */
    fun requestSync() {
        SyncWorker.enqueueOneOff(appContext)
    }

    suspend fun seedIfEmpty() {
        if (vehicleRepository.count() > 0) return
        SeedData.vehicles().forEach { vehicleRepository.upsert(it) }
        SeedData.logEntries().forEach { logRepository.upsert(it) }
        SeedData.issues().forEach { issueRepository.upsert(it) }
        SeedData.buildPhases().forEach { buildPhaseRepository.upsert(it) }
        SeedData.buildSteps().forEach { buildStepRepository.upsert(it) }
        SeedData.maintenanceSchedules().forEach { scheduleRepository.upsert(it) }
    }
}
