package com.garagelog.app.data.backup

import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.PhotoRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.data.seed.SampleData
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

class BackupManager(
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
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
        // Sample data stays out of backups, like it stays out of sync (see SampleData).
        val data = BackupData(
            vehicles = vehicleRepository.getAll().filterNot { SampleData.isSample(it.id) }.map { it.toBackup() },
            logs = logRepository.getAll().filterNot { SampleData.isSample(it.vehicleId) }.map { it.toBackup() },
            issues = issueRepository.getAll().filterNot { SampleData.isSample(it.vehicleId) }.map { it.toBackup() },
            maintenanceSchedules = scheduleRepository.getAll().filterNot { SampleData.isSample(it.vehicleId) }.map { it.toBackup() },
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
        data.maintenanceSchedules.forEach { scheduleRepository.upsert(it.toEntity()) }
    }

    /** Settings → "Reset to sample data": everything on this phone goes, and the two sample vehicles come back. */
    suspend fun resetToSample() {
        wipeAllData()
        addSamples()
    }

    /** Loads the sample vehicles (SampleData), dated from today. Their ids are fixed, so loading twice just refreshes them. */
    suspend fun addSamples() {
        val sample = SampleData.build()
        // Added back beside real vehicles, they go at the end of the list rather than the top.
        val after = vehicleRepository.getAll().filterNot { SampleData.isSample(it.id) }.maxOfOrNull { it.sortOrder + 1 } ?: 0
        sample.vehicles.forEach { vehicleRepository.upsert(it.copy(sortOrder = it.sortOrder + after)) }
        sample.logs.forEach { logRepository.upsert(it) }
        sample.issues.forEach { issueRepository.upsert(it) }
        sample.schedules.forEach { scheduleRepository.upsert(it) }
    }

    private suspend fun wipeAllData() {
        photoRepository.getAll().forEach { photoStore.delete(it.filePath) }
        photoRepository.deleteAll()
        vehicleRepository.deleteAll()
        logRepository.deleteAll()
        issueRepository.deleteAll()
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
    trim = trim, bodyStyle = bodyStyle, cabStyle = cabStyle, bedLength = bedLength,
    transmissionType = transmissionType, transmissionSpeeds = transmissionSpeeds, cylinders = cylinders,
    displacementL = displacementL, fuelType = fuelType, aspiration = aspiration,
)

private fun BackupVehicle.toEntity(sortOrder: Int) = VehicleEntity(
    id = id, name = name.ifBlank { "Unnamed vehicle" }, year = year?.trim()?.toDoubleOrNull()?.toInt(),
    make = make, model = model, engine = engine, drivetrain = drivetrain, vin = vin, color = color,
    miles = miles?.trim()?.toDoubleOrNull()?.toInt(), milesDate = milesDate, role = role, notes = notes,
    sortOrder = sortOrder, updatedAt = System.currentTimeMillis(),
    severeDustyAreas = severeDustyAreas, severeTowing = severeTowing, severeExtendedIdling = severeExtendedIdling,
    severeLowSpeedColdWeather = severeLowSpeedColdWeather, severeHeavyCityTrafficHot = severeHeavyCityTrafficHot,
    severeMountainousHot = severeMountainousHot, severeFrequentTowing = severeFrequentTowing, severeDeepWater = severeDeepWater,
    trim = trim, bodyStyle = bodyStyle, cabStyle = cabStyle, bedLength = bedLength,
    transmissionType = transmissionType, transmissionSpeeds = transmissionSpeeds, cylinders = cylinders,
    displacementL = displacementL, fuelType = fuelType, aspiration = aspiration,
)

private fun LogEntryEntity.toBackup() = BackupLog(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.toString(), category = category,
    task = task, cost = cost?.toString(), parts = parts, notes = notes,
    gallons = gallons?.toString(), fullTank = fullTank, missedFillUp = missedFillUp,
    createdBy = createdBy, createdByName = createdByName, inProgress = inProgress,
)

private fun BackupLog.toEntity() = LogEntryEntity(
    id = id, vehicleId = vehicleId, date = date, mileage = mileage?.trim()?.toDoubleOrNull()?.toInt(),
    category = category, task = task.ifBlank { "Untitled entry" },
    cost = cost?.trim()?.toDoubleOrNull(), parts = parts, notes = notes,
    gallons = gallons?.trim()?.toDoubleOrNull(), fullTank = fullTank, missedFillUp = missedFillUp,
    createdBy = createdBy, createdByName = createdByName, inProgress = inProgress,
    updatedAt = System.currentTimeMillis(),
)

private fun IssueEntity.toBackup() = BackupIssue(
    id = id, vehicleId = vehicleId, title = title, status = status, priority = priority,
    dateOpened = dateOpened, dateResolved = dateResolved, description = description,
    createdBy = createdBy, createdByName = createdByName,
)

private fun BackupIssue.toEntity() = IssueEntity(
    id = id, vehicleId = vehicleId, title = title.ifBlank { "Untitled issue" }, status = status,
    priority = priority, dateOpened = dateOpened, dateResolved = dateResolved, description = description,
    createdBy = createdBy, createdByName = createdByName,
    updatedAt = System.currentTimeMillis(),
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
