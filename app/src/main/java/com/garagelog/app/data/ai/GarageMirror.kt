package com.garagelog.app.data.ai

import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.todayIso
import com.garagelog.app.util.vehicleSpecLines
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors the app's records into the read-only memory store Bob looks things up in, so a request
 * carries only the question rather than a whole vehicle's history.
 *
 * Stateless on purpose: every sync renders the files from Room, lists the store, and compares
 * content hashes, so nothing on the device can drift out of step with the store. Only files whose
 * content changed are uploaded; a typical sync is one list call and nothing else.
 *
 * Like [GarageContext], leaves the VIN out — Bob searches the web, and it identifies the owner.
 */
class GarageMirror(
    private val api: ManagedAgentsApi,
    private val storeId: String,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
) {
    /** A diagnosis and a chat can start together; one sync at a time keeps them from racing. */
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        val desired = render()
        val remote = api.listMemories(storeId).associateBy { it.path }
        for ((path, content) in desired) {
            val existing = remote[path]
            when {
                existing == null -> api.createMemory(storeId, path, content)
                existing.sha256 != sha256(content) -> api.updateMemory(storeId, existing.id, content)
            }
        }
        // A deleted vehicle, or a year whose last entry was removed.
        remote.values.filter { it.path !in desired }.forEach { api.deleteMemory(storeId, it.id) }
    }

    /** The folder Bob's notes and these records use for [vehicle], e.g. "vehicles/blue-tuna/". */
    suspend fun folderFor(vehicle: VehicleEntity): String = "vehicles/${slugs(vehicleRepository.getAll())[vehicle.id]}/"

    private suspend fun render(): Map<String, String> {
        val vehicles = vehicleRepository.getAll().sortedBy { it.sortOrder }
        val slugs = slugs(vehicles)
        val logs = logRepository.getAll().groupBy { it.vehicleId }
        val issues = issueRepository.getAll().groupBy { it.vehicleId }
        val schedules = scheduleRepository.getAll().groupBy { it.vehicleId }

        val files = LinkedHashMap<String, String>()
        files["/index.md"] = buildString {
            appendLine("# Vehicles (records as of ${todayIso()})")
            appendLine()
            vehicles.forEach { appendLine("- ${GarageContext.identity(it)} — vehicles/${slugs.getValue(it.id)}/") }
        }
        for (vehicle in vehicles) {
            val dir = "/vehicles/${slugs.getValue(vehicle.id)}"
            val vehicleLogs = logs[vehicle.id].orEmpty()
            files["$dir/specs.md"] = specs(vehicle, vehicleLogs)
            files["$dir/schedule.md"] = schedule(vehicle, schedules[vehicle.id].orEmpty())
            files["$dir/issues.md"] = issuesFile(vehicle, issues[vehicle.id].orEmpty())
            files["$dir/fuel.md"] = fuel(vehicle, vehicleLogs)
            vehicleLogs
                .filter { it.category != LogCategory.Fuel.name && it.category != LogCategory.Mileage.name }
                .groupBy { it.date.take(4).takeIf { year -> year.length == 4 && year.all(Char::isDigit) } ?: "undated" }
                .forEach { (year, entries) -> files["$dir/service-$year.md"] = service(vehicle, year, entries) }
        }
        return files
    }

    private fun specs(vehicle: VehicleEntity, logs: List<LogEntryEntity>) = buildString {
        appendLine("# ${GarageContext.identity(vehicle)}")
        appendLine()
        vehicleSpecLines(vehicle, includePersonal = false).forEach { (label, value) -> appendLine("$label: $value") }
        vehicle.miles?.let { appendLine("Current odometer: $it miles (as of ${vehicle.milesDate.ifBlank { "unknown date" }})") }
        vehicle.role.takeIf { it.isNotBlank() }?.let { appendLine("Role: $it") }
        vehicle.notes.takeIf { it.isNotBlank() }?.let { appendLine("Owner notes: $it") }
        GarageContext.severeDutyConditions(vehicle).takeIf { it.isNotEmpty() }?.let {
            appendLine("Severe-duty use: ${it.joinToString(", ")} (owner-reported; OEM guidance is to halve service intervals)")
        }
        GarageContext.fuelEconomy(logs)?.let { appendLine(it) }
    }

    private fun schedule(vehicle: VehicleEntity, schedules: List<MaintenanceScheduleEntity>) = buildString {
        appendLine("# ${vehicle.name}: maintenance schedule")
        appendLine()
        if (schedules.isEmpty()) appendLine("No maintenance schedule set up.")
        schedules.sortedBy { it.taskName.lowercase() }.forEach { schedule ->
            val due = computeDueInfo(schedule, vehicle.miles, vehicle.isSevereDuty)
            val interval = listOfNotNull(schedule.intervalMiles?.let { formatMiles(it) }, schedule.intervalMonths?.let { "$it mo" })
                .joinToString(" / ").ifEmpty { "no interval" }
            val lastDone = if (schedule.lastDoneDate != null || schedule.lastDoneMileage != null) {
                "last done ${schedule.lastDoneDate ?: "?"} at ${schedule.lastDoneMileage?.toString() ?: "?"} mi"
            } else {
                "never recorded"
            }
            appendLine("- ${schedule.taskName} (every $interval) — $lastDone — ${due.status}: ${due.label}")
        }
    }

    private fun issuesFile(vehicle: VehicleEntity, issues: List<IssueEntity>) = buildString {
        appendLine("# ${vehicle.name}: issues")
        val (resolved, open) = issues.partition { it.status == IssueStatus.Resolved.name }
        appendLine()
        appendLine("## Open")
        if (open.isEmpty()) appendLine("None.")
        open.sortedByDescending { it.dateOpened }.forEach { appendLine("- ${GarageContext.issueLine(it)}") }
        appendLine()
        appendLine("## Resolved")
        if (resolved.isEmpty()) appendLine("None.")
        resolved.sortedByDescending { it.dateResolved }.forEach {
            appendLine("- ${GarageContext.issueLine(it)}${if (it.dateResolved.isNotBlank()) " — resolved ${it.dateResolved}" else ""}")
        }
    }

    private fun fuel(vehicle: VehicleEntity, logs: List<LogEntryEntity>) = buildString {
        appendLine("# ${vehicle.name}: fill-ups and mileage readings (newest first)")
        appendLine()
        val readings = logs.filter { it.category == LogCategory.Fuel.name || it.category == LogCategory.Mileage.name }
            .sortedByDescending { it.date }
        if (readings.isEmpty()) appendLine("None recorded.")
        readings.forEach { entry ->
            append("- ${entry.date}")
            entry.mileage?.let { append(" @ $it mi") }
            if (entry.category == LogCategory.Fuel.name) {
                entry.gallons?.let { append(" — ${"%.2f".format(it)} gal") }
                append(if (entry.fullTank) " (full tank)" else " (partial)")
                if (entry.missedFillUp) append(" (a fill-up before this one wasn't logged)")
                entry.cost?.let { append(" — \$${"%.2f".format(it)}") }
            } else {
                append(" — odometer reading")
            }
            entry.notes.takeIf { it.isNotBlank() }?.let { append(" — notes: $it") }
            appendLine()
        }
    }

    private fun service(vehicle: VehicleEntity, year: String, entries: List<LogEntryEntity>) = buildString {
        appendLine("# ${vehicle.name}: service history, $year (newest first)")
        appendLine()
        entries.sortedByDescending { it.date }.forEach { appendLine("- ${GarageContext.serviceLine(it)}") }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * Nickname in lowercase, hyphens for anything else — the same naming Bob's own notes use
         * (vehicles/blue-tuna.md). Two vehicles with the same nickname get -2, -3 in sort order.
         */
        fun slugs(vehicles: List<VehicleEntity>): Map<String, String> {
            val taken = HashSet<String>()
            return vehicles.sortedBy { it.sortOrder }.associate { vehicle ->
                val base = vehicle.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "vehicle" }
                var slug = base
                var n = 2
                while (!taken.add(slug)) slug = "$base-${n++}"
                vehicle.id to slug
            }
        }
    }
}
