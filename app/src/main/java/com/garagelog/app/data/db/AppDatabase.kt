package com.garagelog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.garagelog.app.data.dao.BuildPhaseDao
import com.garagelog.app.data.dao.BuildStepDao
import com.garagelog.app.data.dao.IssueDao
import com.garagelog.app.data.dao.LogEntryDao
import com.garagelog.app.data.dao.MaintenanceScheduleDao
import com.garagelog.app.data.dao.NotificationPrefsDao
import com.garagelog.app.data.dao.PhotoDao
import com.garagelog.app.data.dao.VehicleDao
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        LogEntryEntity::class,
        IssueEntity::class,
        BuildPhaseEntity::class,
        BuildStepEntity::class,
        MaintenanceScheduleEntity::class,
        PhotoEntity::class,
        NotificationPrefsEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun issueDao(): IssueDao
    abstract fun buildPhaseDao(): BuildPhaseDao
    abstract fun buildStepDao(): BuildStepDao
    abstract fun maintenanceScheduleDao(): MaintenanceScheduleDao
    abstract fun photoDao(): PhotoDao
    abstract fun notificationPrefsDao(): NotificationPrefsDao

    companion object {
        const val DATABASE_NAME = "garage_log.db"
    }
}
