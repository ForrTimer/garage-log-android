package com.garagelog.app.data.report

import com.garagelog.app.data.dao.AiDiagnosisDao
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssuePriority
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.FillUp
import com.garagelog.app.util.categoryTotals
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.drivingRate
import com.garagelog.app.util.fillUpMpg
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney
import com.garagelog.app.util.monthlyTotals
import com.garagelog.app.util.odometerPoints
import com.garagelog.app.util.vehicleSpecLines

/**
 * Everything one vehicle's report says, as plain data — laid out by [ReportPdfWriter]. Written
 * for someone looking at the vehicle to buy it: what it is, how it's been kept, then the detail.
 */
data class VehicleReport(
    val title: String,
    val subtitle: String,
    val generatedOn: String,
    val specs: List<Pair<String, String>>,
    /** Headline figures, (value, label). */
    val summary: List<Pair<String, String>>,
    val maintenance: List<MaintenanceLine>,
    val odometer: List<Pair<String, Float>>,
    val mpg: List<FillUp>,
    val serviceByMonth: List<Pair<String, Double>>,
    val fuelByMonth: List<Pair<String, Double>>,
    val byCategory: List<Pair<String, Double>>,
    val openIssues: List<ReportIssue>,
    val resolvedIssues: List<ReportIssue>,
    /** Every log entry, newest first. */
    val logs: List<LogEntryEntity>,
)

data class MaintenanceLine(
    val task: String,
    val interval: String,
    val lastDone: String,
    val status: DueStatus,
    val statusLabel: String,
)

data class ReportIssue(
    val title: String,
    val status: String,
    val safetyCritical: Boolean,
    val opened: String,
    val resolved: String,
    val description: String,
    /** The gist of Bob's diagnosis (Markdown, one paragraph), if one was run. See [diagnosisSummary]. */
    val diagnosis: String?,
)

/** Months of spend the report charts — a year, where the Trends tab fits six on a phone. */
private const val REPORT_MONTHS = 12

fun assembleReport(
    vehicle: VehicleEntity,
    logs: List<LogEntryEntity>,
    issues: List<IssueEntity>,
    schedules: List<MaintenanceScheduleEntity>,
    diagnoses: Map<String, String>,
    today: String,
): VehicleReport {
    val (fuelLogs, serviceLogs) = logs.filter { it.cost != null }.partition { it.category == LogCategory.Fuel.name }
    val totalSpend = logs.sumOf { it.cost ?: 0.0 }
    val mpg = fillUpMpg(logs)
    val rate = drivingRate(logs)
    val costPerMile = rate?.basisMiles?.takeIf { it > 0 && totalSpend > 0 }?.let { totalSpend / it }
    val identity = listOfNotNull(vehicle.year?.toString(), vehicle.make.ifBlank { null }, vehicle.model.ifBlank { null })
        .joinToString(" ").ifBlank { "Vehicle" }

    fun toReportIssue(issue: IssueEntity) = ReportIssue(
        title = issue.title,
        status = issue.status,
        safetyCritical = issue.priority == IssuePriority.SafetyCritical.label,
        opened = formatDate(issue.dateOpened),
        resolved = issue.dateResolved.takeIf { it.isNotBlank() }?.let { formatDate(it) } ?: "",
        description = issue.description,
        diagnosis = diagnoses[issue.id]?.let(::diagnosisSummary),
    )
    val (resolved, open) = issues.partition { it.status == IssueStatus.Resolved.label }

    return VehicleReport(
        title = identity,
        subtitle = listOfNotNull(
            vehicle.name.takeIf { it.isNotBlank() && it != identity },
            vehicle.miles?.let { "${formatMiles(it)} as of ${formatDate(vehicle.milesDate.ifBlank { today })}" },
        ).joinToString(" · "),
        generatedOn = formatDate(today),
        // The VIN stays (a buyer checks it); the owner's free-text notes and role don't: notes can
        // hold anything, like who they bought it from and what they paid.
        specs = vehicleSpecLines(vehicle, includePersonal = true).filter { (label, _) -> label != "Role" && label != "Notes" },
        summary = buildList {
            add(formatMiles(vehicle.miles).removeSuffix(" mi") to "miles")
            add(formatMoney(serviceLogs.sumOf { it.cost ?: 0.0 }) to "service & repairs logged")
            if (fuelLogs.isNotEmpty()) add(formatMoney(fuelLogs.sumOf { it.cost ?: 0.0 }) to "fuel logged")
            if (mpg.isNotEmpty()) add("%.1f".format(mpg.map { it.mpg }.average()) to "average mpg")
            costPerMile?.let { add("$" + "%.2f".format(it) to "per mile, fuel + service") }
            add(logs.size.toString() to "log entries")
        },
        maintenance = schedules.sortedBy { it.taskName.lowercase() }.map { schedule ->
            val info = computeDueInfo(schedule, vehicle.miles, vehicle.isSevereDuty)
            MaintenanceLine(
                task = schedule.taskName,
                interval = listOfNotNull(
                    schedule.intervalMiles?.let { "every ${formatMiles(it)}" },
                    schedule.intervalMonths?.let { "every $it mo" },
                ).joinToString(" · ").ifBlank { "—" },
                lastDone = listOfNotNull(
                    schedule.lastDoneDate?.let { formatDate(it) },
                    schedule.lastDoneMileage?.let { formatMiles(it) },
                ).joinToString(" · ").ifBlank { "—" },
                status = info.status,
                // The app's "mark done once to start tracking" is an instruction; a report just states it.
                statusLabel = if (info.status == DueStatus.UNKNOWN) {
                    if (schedule.intervalMiles == null && schedule.intervalMonths == null) "No interval set" else "Not recorded yet"
                } else {
                    info.label
                },
            )
        },
        odometer = odometerPoints(logs),
        mpg = mpg,
        serviceByMonth = monthlyTotals(serviceLogs, REPORT_MONTHS),
        fuelByMonth = monthlyTotals(fuelLogs, REPORT_MONTHS),
        byCategory = categoryTotals(logs.filter { it.cost != null }).filter { it.second > 0 },
        openIssues = open.sortedByDescending { it.dateOpened }.map(::toReportIssue),
        resolvedIssues = resolved.sortedByDescending { it.dateResolved.ifBlank { it.dateOpened } }.map(::toReportIssue),
        logs = logs.sortedWith(compareByDescending<LogEntryEntity> { it.date }.thenByDescending { it.mileage ?: 0 }),
    )
}

/** Loads one vehicle's live records and assembles its report. */
class VehicleReportBuilder(
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val diagnosisDao: AiDiagnosisDao,
) {
    suspend fun build(vehicleId: String, today: String): VehicleReport {
        val vehicle = vehicleRepository.getById(vehicleId)?.takeUnless { it.deleted } ?: error("That vehicle no longer exists.")
        val issues = issueRepository.getAll().filter { it.vehicleId == vehicleId }
        return assembleReport(
            vehicle = vehicle,
            logs = logRepository.getAll().filter { it.vehicleId == vehicleId },
            issues = issues,
            schedules = scheduleRepository.getAll().filter { it.vehicleId == vehicleId },
            diagnoses = issues.mapNotNull { issue -> diagnosisDao.getForIssue(issue.id)?.let { issue.id to it.content } }.toMap(),
            today = today,
        )
    }
}

/**
 * The part of a diagnosis a buyer needs: Bob's TL;DR line, or for older diagnoses written before
 * he gave one, his "Most likely cause" paragraph. The rest is written to the owner, not to a
 * reader of the report, and runs to pages (it also mentions things like what the owner paid).
 */
internal fun diagnosisSummary(markdown: String): String? {
    val lines = markdown.lines().map { it.trim() }
    val tldr = Regex("""^\**TL;DR:?\**:?\s*""", RegexOption.IGNORE_CASE)
    lines.firstOrNull { tldr.containsMatchIn(it) }?.let { line ->
        return line.replaceFirst(tldr, "").trim().ifBlank { null }
    }
    val heading = lines.indexOfFirst { it.startsWith("#") && it.contains("most likely cause", ignoreCase = true) }
    val body = if (heading >= 0) lines.drop(heading + 1) else lines.dropWhile { it.isBlank() || it.startsWith("#") }
    return body.dropWhile { it.isBlank() }.takeWhile { it.isNotBlank() && !it.startsWith("#") }
        .joinToString(" ").ifBlank { null }
}
