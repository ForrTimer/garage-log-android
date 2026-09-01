package com.garagelog.app.data.backup

import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.repository.BuildPhaseRepository
import com.garagelog.app.data.repository.BuildStepRepository
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
    private val buildStepRepository: BuildStepRepository,
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
            buildSteps = buildStepRepository.getAll().map { it.toBackup() },
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
        data.buildSteps.forEach { buildStepRepository.upsert(it.toEntity()) }
        data.maintenanceSchedules.forEach { scheduleRepository.upsert(it.toEntity()) }
    }

    suspend fun resetToSeed() {
        wipeAllData()
        // Stamped "now" (not the entities' default 0L) so a reset genuinely wins on sync,
        // rather than silently losing to whatever's already sitting in Drive.
        val now = System.currentTimeMillis()
        SeedData.vehicles().forEach { vehicleRepository.upsert(it.copy(updatedAt = now)) }
        SeedData.logEntries().forEach { logRepository.upsert(it.copy(updatedAt = now)) }
        SeedData.issues().forEach { issueRepository.upsert(it.copy(updatedAt = now)) }
        SeedData.buildPhases().forEach { buildPhaseRepository.upsert(it.copy(updatedAt = now)) }
        SeedData.maintenanceSchedules().forEach { scheduleRepository.upsert(it.copy(updatedAt = now)) }
    }

    private suspend fun wipeAllData() {
        photoRepository.getAll().forEach { photoStore.delete(it.filePath) }
        photoRepository.deleteAll()
        vehicleRepository.deleteAll()
        logRepository.deleteAll()
        issueRepository.deleteAll()
        buildPhaseRepository.deleteAll()
        buildStepRepository.deleteAll()
        scheduleRepository.deleteAll()
    }
}

private fun VehicleEntity.toBackup() = BackupVehicle(
    id = id, name = name, year = year?.toString(), make = make, model = model, engine = engine,
    drivetrain = drivetrain, vin = vin, color = color, miles = miles?.toString(), milesDate = milesDate,
    role = role, notes = notes,
    severeDustyAreas = severeDustyAreas, severeTowing = severeTowing, severeExtendedIdling = severeExtendedIdling,
    severeLowSpeedColdWeather = severeLowSpeedColdWeather, severeHeavyCityTrafficHot = severeHeavyCityTrafficHot,
    severeMountainousHot = severeMountainousHot, severeFrequentTowing = severeFrequentTowing, severeDeepWater = severeDeepWater,
)

private fun BackupVehicle.toEntity(sortOrder: Int) = VehicleEntity(
    id = id, name = name.ifBlank { "Unnamed vehicle" }, year = year?.trim()?.toDoubleOrNull()?.toInt(),
    make = make, model = model, engine = engine, drivetrain = drivetrain, vin = vin, color = color,
    miles = miles?.trim()?.toDoubleOrNull()?.toInt(), milesDate = milesDate, role = role, notes = notes,
    sortOrder = sortOrder, updatedAt = System.currentTimeMillis(),
    severeDustyAreas = severeDustyAreas, severeTowing = severeTowing, severeExtendedIdling = severeExtendedIdling,
    severeLowSpeedColdWeather = severeLowSpeedColdWeather, severeHeavyCityTrafficHot = severeHeavyCityTrafficHot,
    severeMountainousHot = severeMountainousHot, severeFrequentTowing = severeFrequentTowing, severeDeepWater = severeDeepWater,
)

private fun LogEntryEntity.toBackup() = BackupLog(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.toString(), category = category,
    task = task, cost = cost?.toString(), parts = parts, notes = notes,
)

private fun BackupLog.toEntity() = LogEntryEntity(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.trim()?.toDoubleOrNull()?.toInt(),
    category = category, task = task.ifBlank { "Untitled entry" },
    cost = cost?.trim()?.toDoubleOrNull(), parts = parts, notes = notes,
    updatedAt = System.currentTimeMillis(),
)

private fun IssueEntity.toBackup() = BackupIssue(
    id = id, vehicleId = vehicleId, title = title, status = status, priority = priority,
    dateOpened = dateOpened, dateResolved = dateResolved, description = description,
)

private fun BackupIssue.toEntity() = IssueEntity(
    id = id, vehicleId = vehicleId, title = title.ifBlank { "Untitled issue" }, status = status,
    priority = priority, dateOpened = dateOpened, dateResolved = dateResolved, description = description,
    updatedAt = System.currentTimeMillis(),
)

private fun BuildPhaseEntity.toBackup() = BackupPhase(
    id = id, vehicleId = vehicleId, phase = phase, status = status, order = order.toString(), notes = notes,
    priorityFilter = priorityFilter, budgetCap = budgetCap?.toString(),
)

private fun BackupPhase.toEntity() = BuildPhaseEntity(
    id = id, vehicleId = vehicleId, phase = phase.ifBlank { "Untitled phase" }, status = status,
    order = order?.trim()?.toDoubleOrNull()?.toInt() ?: 0, notes = notes,
    priorityFilter = priorityFilter, budgetCap = budgetCap?.trim()?.toDoubleOrNull(),
    updatedAt = System.currentTimeMillis(),
)

private fun BuildStepEntity.toBackup() = BackupStep(
    id = id, vehicleId = vehicleId, phaseId = phaseId, title = title, notes = notes, priority = priority,
    status = status, estimatedCost = estimatedCost?.toString(), actualCost = actualCost?.toString(),
    order = order.toString(), manualPhaseOverride = manualPhaseOverride,
)

private fun BackupStep.toEntity() = BuildStepEntity(
    id = id, vehicleId = vehicleId, phaseId = phaseId, title = title.ifBlank { "Untitled step" }, notes = notes,
    priority = priority, status = status, estimatedCost = estimatedCost?.trim()?.toDoubleOrNull(),
    actualCost = actualCost?.trim()?.toDoubleOrNull(), order = order?.trim()?.toDoubleOrNull()?.toInt() ?: 0,
    manualPhaseOverride = manualPhaseOverride, updatedAt = System.currentTimeMillis(),
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
    updatedAt = System.currentTimeMillis(),
)
