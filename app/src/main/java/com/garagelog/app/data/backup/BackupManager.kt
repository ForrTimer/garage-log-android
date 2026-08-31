package com.garagelog.app.data.backup

import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.repository.BuildPhaseRepository
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.PhotoRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.data.seed.SeedData
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

class BackupManager(
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val buildPhaseRepository: BuildPhaseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val photoRepository: PhotoRepository,
    private val photoStore: PhotoStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun exportToStream(output: OutputStream) {
        val data = BackupData(
            vehicles = vehicleRepository.getAll().map { it.toBackup() },
            logs = logRepository.getAll().map { it.toBackup() },
            issues = issueRepository.getAll().map { it.toBackup() },
            buildPhases = buildPhaseRepository.getAll().map { it.toBackup() },
            maintenanceSchedules = scheduleRepository.getAll().map { it.toBackup() },
        )
        val text = json.encodeToString(BackupData.serializer(), data)
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun importFromStream(input: InputStream): Result<Unit> = runCatching {
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val data = json.decodeFromString(BackupData.serializer(), text)
        require(data.vehicles.isNotEmpty()) { "That doesn't look like a Garage Log backup file (no vehicles found)." }
        wipeAllData()
        data.vehicles.forEachIndexed { index, v -> vehicleRepository.upsert(v.toEntity(index)) }
        data.logs.forEach { logRepository.upsert(it.toEntity()) }
        data.issues.forEach { issueRepository.upsert(it.toEntity()) }
        data.buildPhases.forEach { buildPhaseRepository.upsert(it.toEntity()) }
        data.maintenanceSchedules.forEach { scheduleRepository.upsert(it.toEntity()) }
    }

    suspend fun resetToSeed() {
        wipeAllData()
        SeedData.vehicles().forEach { vehicleRepository.upsert(it) }
        SeedData.logEntries().forEach { logRepository.upsert(it) }
        SeedData.issues().forEach { issueRepository.upsert(it) }
        SeedData.buildPhases().forEach { buildPhaseRepository.upsert(it) }
        SeedData.maintenanceSchedules().forEach { scheduleRepository.upsert(it) }
    }

    private suspend fun wipeAllData() {
        photoRepository.getAll().forEach { photoStore.delete(it.filePath) }
        photoRepository.deleteAll()
        vehicleRepository.deleteAll()
        logRepository.deleteAll()
        issueRepository.deleteAll()
        buildPhaseRepository.deleteAll()
        scheduleRepository.deleteAll()
    }
}

private fun VehicleEntity.toBackup() = BackupVehicle(
    id = id, name = name, year = year?.toString(), make = make, model = model, engine = engine,
    drivetrain = drivetrain, vin = vin, color = color, miles = miles?.toString(), milesDate = milesDate,
    role = role, notes = notes,
)

private fun BackupVehicle.toEntity(sortOrder: Int) = VehicleEntity(
    id = id, name = name.ifBlank { "Unnamed vehicle" }, year = year?.trim()?.toDoubleOrNull()?.toInt(),
    make = make, model = model, engine = engine, drivetrain = drivetrain, vin = vin, color = color,
    miles = miles?.trim()?.toDoubleOrNull()?.toInt(), milesDate = milesDate, role = role, notes = notes,
    sortOrder = sortOrder,
)

private fun LogEntryEntity.toBackup() = BackupLog(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.toString(), category = category,
    task = task, cost = cost?.toString(), parts = parts, notes = notes,
)

private fun BackupLog.toEntity() = LogEntryEntity(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.trim()?.toDoubleOrNull()?.toInt(),
    category = category, task = task.ifBlank { "Untitled entry" },
    cost = cost?.trim()?.toDoubleOrNull(), parts = parts, notes = notes,
)

private fun IssueEntity.toBackup() = BackupIssue(
    id = id, vehicleId = vehicleId, title = title, status = status, priority = priority,
    dateOpened = dateOpened, dateResolved = dateResolved, description = description,
)

private fun BackupIssue.toEntity() = IssueEntity(
    id = id, vehicleId = vehicleId, title = title.ifBlank { "Untitled issue" }, status = status,
    priority = priority, dateOpened = dateOpened, dateResolved = dateResolved, description = description,
)

private fun BuildPhaseEntity.toBackup() = BackupPhase(
    id = id, vehicleId = vehicleId, phase = phase, status = status, order = order.toString(), notes = notes,
)

private fun BackupPhase.toEntity() = BuildPhaseEntity(
    id = id, vehicleId = vehicleId, phase = phase.ifBlank { "Untitled phase" }, status = status,
    order = order?.trim()?.toDoubleOrNull()?.toInt() ?: 0, notes = notes,
)

private fun MaintenanceScheduleEntity.toBackup() = BackupSchedule(
    id = id, vehicleId = vehicleId, taskName = taskName, intervalMiles = intervalMiles?.toString(),
    intervalMonths = intervalMonths?.toString(), lastDoneMileage = lastDoneMileage?.toString(),
    lastDoneDate = lastDoneDate,
)

private fun BackupSchedule.toEntity() = MaintenanceScheduleEntity(
    id = id, vehicleId = vehicleId, taskName = taskName.ifBlank { "Untitled schedule" },
    intervalMiles = intervalMiles?.trim()?.toDoubleOrNull()?.toInt(),
    intervalMonths = intervalMonths?.trim()?.toDoubleOrNull()?.toInt(),
    lastDoneMileage = lastDoneMileage?.trim()?.toDoubleOrNull()?.toInt(),
    lastDoneDate = lastDoneDate,
)
