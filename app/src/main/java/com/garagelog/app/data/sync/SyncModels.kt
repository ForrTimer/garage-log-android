package com.garagelog.app.data.sync

import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.serialization.Serializable

/**
 * The single JSON snapshot pushed to/pulled from Drive's appDataFolder. Deliberately separate
 * from the manual-backup DTOs in data/backup — that's a point-in-time export for the owner;
 * this is an ongoing merge format and carries the sync bookkeeping fields (updatedAt/deleted)
 * the backup format has no business knowing about. Photos are NOT included here — their bytes
 * live as separate Drive files, reconciled by SyncRepository via appProperties tags, not by
 * this snapshot (only their existence needs to be knowable, and Drive's file listing already
 * gives us that).
 */
@Serializable
data class SyncSnapshot(
    val vehicles: List<SyncVehicle> = emptyList(),
    val logs: List<SyncLog> = emptyList(),
    val issues: List<SyncIssue> = emptyList(),
    val buildPhases: List<SyncPhase> = emptyList(),
    val schedules: List<SyncSchedule> = emptyList(),
)

interface Syncable {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
}

@Serializable
data class SyncVehicle(
    override val id: String,
    val name: String,
    val year: Int?,
    val make: String,
    val model: String,
    val engine: String,
    val drivetrain: String,
    val vin: String,
    val color: String,
    val miles: Int?,
    val milesDate: String,
    val role: String,
    val notes: String,
    val sortOrder: Int,
    override val updatedAt: Long,
    override val deleted: Boolean,
) : Syncable

@Serializable
data class SyncLog(
    override val id: String,
    val vehicleId: String,
    val date: String,
    val mileage: Int?,
    val category: String,
    val task: String,
    val cost: Double?,
    val parts: String,
    val notes: String,
    override val updatedAt: Long,
    override val deleted: Boolean,
) : Syncable

@Serializable
data class SyncIssue(
    override val id: String,
    val vehicleId: String,
    val title: String,
    val status: String,
    val priority: String,
    val dateOpened: String,
    val dateResolved: String,
    val description: String,
    override val updatedAt: Long,
    override val deleted: Boolean,
) : Syncable

@Serializable
data class SyncPhase(
    override val id: String,
    val vehicleId: String,
    val phase: String,
    val status: String,
    val order: Int,
    val notes: String,
    override val updatedAt: Long,
    override val deleted: Boolean,
) : Syncable

@Serializable
data class SyncSchedule(
    override val id: String,
    val vehicleId: String,
    val taskName: String,
    val intervalMiles: Int?,
    val intervalMonths: Int?,
    val lastDoneMileage: Int?,
    val lastDoneDate: String?,
    override val updatedAt: Long,
    override val deleted: Boolean,
) : Syncable

fun VehicleEntity.toSync() = SyncVehicle(
    id = id, name = name, year = year, make = make, model = model, engine = engine, drivetrain = drivetrain,
    vin = vin, color = color, miles = miles, milesDate = milesDate, role = role, notes = notes,
    sortOrder = sortOrder, updatedAt = updatedAt, deleted = deleted,
)

fun SyncVehicle.toEntity() = VehicleEntity(
    id = id, name = name, year = year, make = make, model = model, engine = engine, drivetrain = drivetrain,
    vin = vin, color = color, miles = miles, milesDate = milesDate, role = role, notes = notes,
    sortOrder = sortOrder, updatedAt = updatedAt, deleted = deleted,
)

fun LogEntryEntity.toSync() = SyncLog(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage, category = category, task = task,
    cost = cost, parts = parts, notes = notes, updatedAt = updatedAt, deleted = deleted,
)

fun SyncLog.toEntity() = LogEntryEntity(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage, category = category, task = task,
    cost = cost, parts = parts, notes = notes, updatedAt = updatedAt, deleted = deleted,
)

fun IssueEntity.toSync() = SyncIssue(
    id = id, vehicleId = vehicleId, title = title, status = status, priority = priority,
    dateOpened = dateOpened, dateResolved = dateResolved, description = description,
    updatedAt = updatedAt, deleted = deleted,
)

fun SyncIssue.toEntity() = IssueEntity(
    id = id, vehicleId = vehicleId, title = title, status = status, priority = priority,
    dateOpened = dateOpened, dateResolved = dateResolved, description = description,
    updatedAt = updatedAt, deleted = deleted,
)

fun BuildPhaseEntity.toSync() = SyncPhase(
    id = id, vehicleId = vehicleId, phase = phase, status = status, order = order, notes = notes,
    updatedAt = updatedAt, deleted = deleted,
)

fun SyncPhase.toEntity() = BuildPhaseEntity(
    id = id, vehicleId = vehicleId, phase = phase, status = status, order = order, notes = notes,
    updatedAt = updatedAt, deleted = deleted,
)

fun MaintenanceScheduleEntity.toSync() = SyncSchedule(
    id = id, vehicleId = vehicleId, taskName = taskName, intervalMiles = intervalMiles,
    intervalMonths = intervalMonths, lastDoneMileage = lastDoneMileage, lastDoneDate = lastDoneDate,
    updatedAt = updatedAt, deleted = deleted,
)

fun SyncSchedule.toEntity() = MaintenanceScheduleEntity(
    id = id, vehicleId = vehicleId, taskName = taskName, intervalMiles = intervalMiles,
    intervalMonths = intervalMonths, lastDoneMileage = lastDoneMileage, lastDoneDate = lastDoneDate,
    updatedAt = updatedAt, deleted = deleted,
)

/** Whole-record last-write-wins merge: newer `updatedAt` wins outright, including tombstones. */
fun <T : Syncable> mergeById(local: List<T>, remote: List<T>): List<T> {
    val byId = LinkedHashMap<String, T>()
    for (item in local) byId[item.id] = item
    for (item in remote) {
        val existing = byId[item.id]
        if (existing == null || item.updatedAt > existing.updatedAt) {
            byId[item.id] = item
        }
    }
    return byId.values.toList()
}
