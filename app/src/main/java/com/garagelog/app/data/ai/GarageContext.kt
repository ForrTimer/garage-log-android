package com.garagelog.app.data.ai

import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.fillUpMpg
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.vehicleSpecLines

/**
 * Turns what the app already knows about a vehicle into the text Bob reads: the files mirrored
 * into his data memory store ([GarageMirror]) and the vehicle line on a schedule request.
 *
 * Deliberately excludes the VIN: it identifies the vehicle and its owner, and web search is
 * enabled on these requests, so it could end up in a third-party search query. Year/make/model/
 * engine is what actually drives TSB and common-fault relevance anyway.
 */
object GarageContext {

    private const val MAX_SERVICE_ENTRIES = 25

    fun vehicleProfile(
        vehicle: VehicleEntity,
        schedules: List<MaintenanceScheduleEntity>,
        logs: List<LogEntryEntity>,
        issues: List<IssueEntity>,
    ): String = buildString {
        appendLine("## Vehicle")
        appendLine(identity(vehicle))
        // includePersonal = false keeps the VIN out (see class doc); year/make/model are in identity.
        vehicleSpecLines(vehicle, includePersonal = false)
            .filterNot { (label, _) -> label in setOf("Year", "Make", "Model") }
            .forEach { (label, value) -> appendLine("$label: $value") }
        vehicle.miles?.let { appendLine("Current odometer: $it miles (as of ${vehicle.milesDate.ifBlank { "unknown date" }})") }
        vehicle.role.blankToNull()?.let { appendLine("Role: $it") }
        vehicle.notes.blankToNull()?.let { appendLine("Owner notes: $it") }

        severeDutyConditions(vehicle).takeIf { it.isNotEmpty() }?.let {
            appendLine("Severe-duty use: ${it.joinToString(", ")} (owner-reported; OEM guidance is to halve service intervals)")
        }

        fuelEconomy(logs)?.let { appendLine(it) }

        appendLine()
        appendLine("## Maintenance schedule")
        if (schedules.isEmpty()) {
            appendLine("No maintenance schedule set up for this vehicle.")
        } else {
            schedules.forEach { schedule ->
                val due = computeDueInfo(schedule, vehicle.miles, vehicle.isSevereDuty)
                val interval = listOfNotNull(
                    schedule.intervalMiles?.let { formatMiles(it) },
                    schedule.intervalMonths?.let { "$it mo" },
                ).joinToString(" / ").ifEmpty { "no interval" }
                val lastDone = if (schedule.lastDoneDate != null || schedule.lastDoneMileage != null) {
                    "last done ${schedule.lastDoneDate ?: "?"} at ${schedule.lastDoneMileage?.toString() ?: "?"} mi"
                } else {
                    "never recorded"
                }
                appendLine("- ${schedule.taskName} (every $interval) — $lastDone — ${due.status}: ${due.label}")
            }
        }

        appendLine()
        appendLine("## Service history (most recent first)")
        val service = logs
            .filter { it.category != LogCategory.Fuel.name && it.category != LogCategory.Mileage.name }
            .sortedByDescending { it.date }
            .take(MAX_SERVICE_ENTRIES)
        if (service.isEmpty()) {
            appendLine("No service history recorded.")
        } else {
            service.forEach { appendLine("- ${serviceLine(it)}") }
            if (logs.size > service.size) appendLine("(older entries omitted)")
        }

        appendLine()
        appendLine("## Open issues")
        val open = issues.filter { it.status != IssueStatus.Resolved.name }
        if (open.isEmpty()) {
            appendLine("No open issues.")
        } else {
            open.forEach { appendLine("- ${issueLine(it)}") }
        }
    }.trim()

    /** The issue under diagnosis, stated separately from the profile's open-issues list. */
    fun issueDetail(issue: IssueEntity): String = buildString {
        appendLine("## The issue to diagnose")
        appendLine("Title: ${issue.title}")
        appendLine("Status: ${IssueStatus.fromLabel(issue.status).label}")
        appendLine("Priority: ${issue.priority}")
        appendLine("Opened: ${issue.dateOpened.ifBlank { "unknown" }}")
        if (issue.description.isBlank()) {
            appendLine("Description: (the owner did not write a description — work from the title)")
        } else {
            appendLine("Description: ${issue.description}")
        }
    }.trim()

    fun identity(vehicle: VehicleEntity): String {
        val spec = listOfNotNull(
            vehicle.year?.toString(),
            vehicle.make.blankToNull(),
            vehicle.model.blankToNull(),
        ).joinToString(" ").ifEmpty { "unspecified vehicle" }
        return if (vehicle.name.isNotBlank() && vehicle.name != spec) "${vehicle.name} — $spec" else spec
    }

    internal fun serviceLine(entry: LogEntryEntity): String = buildString {
        append(entry.date)
        entry.mileage?.let { append(" @ $it mi") }
        append(" — [${entry.category}] ${entry.task.ifBlank { "(no task)" }}")
        entry.parts.blankToNull()?.let { append(" — parts: $it") }
        entry.cost?.let { append(" — \$${"%.2f".format(it)}") }
        entry.notes.blankToNull()?.let { append(" — notes: $it") }
    }

    internal fun issueLine(issue: IssueEntity): String = buildString {
        append(issue.title)
        append(" [${IssueStatus.fromLabel(issue.status).label}")
        append(", ${issue.priority}]")
        if (issue.dateOpened.isNotBlank()) append(" opened ${issue.dateOpened}")
        issue.description.blankToNull()?.let { append(" — $it") }
    }

    /** A downward MPG trend is a real diagnostic signal, so it's worth a line in every profile. */
    internal fun fuelEconomy(logs: List<LogEntryEntity>): String? {
        val fillUps = fillUpMpg(logs)
        if (fillUps.isEmpty()) return null
        val recent = fillUps.takeLast(5)
        val average = recent.map { it.mpg }.average()
        return buildString {
            append("Recent fuel economy: ${"%.1f".format(average)} mpg average over ${recent.size} full-tank fill-up(s)")
            if (fillUps.size > recent.size) {
                val earlier = fillUps.dropLast(recent.size).map { it.mpg }.average()
                append(" (earlier history averaged ${"%.1f".format(earlier)} mpg)")
            }
        }
    }

    internal fun severeDutyConditions(vehicle: VehicleEntity): List<String> = buildList {
        if (vehicle.severeDustyAreas) add("dusty areas")
        if (vehicle.severeTowing) add("towing")
        if (vehicle.severeFrequentTowing) add("frequent towing")
        if (vehicle.severeExtendedIdling) add("extended idling")
        if (vehicle.severeLowSpeedColdWeather) add("low-speed cold-weather driving")
        if (vehicle.severeHeavyCityTrafficHot) add("heavy city traffic in heat")
        if (vehicle.severeMountainousHot) add("mountainous driving in heat")
        if (vehicle.severeDeepWater) add("deep water crossings")
    }

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
}
