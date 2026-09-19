package com.garagelog.app.data.ai

import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.fillUpMpg

/**
 * Turns what the app already knows about a vehicle into the context Claude reasons over.
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
        vehicle.engine.blankToNull()?.let { appendLine("Engine: $it") }
        vehicle.drivetrain.blankToNull()?.let { appendLine("Drivetrain: $it") }
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
                    schedule.intervalMiles?.let { "$it mi" },
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

    private fun serviceLine(entry: LogEntryEntity): String = buildString {
        append(entry.date)
        entry.mileage?.let { append(" @ $it mi") }
        append(" — [${entry.category}] ${entry.task.ifBlank { "(no task)" }}")
        entry.parts.blankToNull()?.let { append(" — parts: $it") }
        entry.cost?.let { append(" — \$${"%.2f".format(it)}") }
        entry.notes.blankToNull()?.let { append(" — notes: $it") }
    }

    private fun issueLine(issue: IssueEntity): String = buildString {
        append(issue.title)
        append(" [${IssueStatus.fromLabel(issue.status).label}")
        append(", ${issue.priority}]")
        if (issue.dateOpened.isNotBlank()) append(" opened ${issue.dateOpened}")
        issue.description.blankToNull()?.let { append(" — $it") }
    }

    /** A downward MPG trend is a real diagnostic signal, so it's worth a line in every profile. */
    private fun fuelEconomy(logs: List<LogEntryEntity>): String? {
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

    private fun severeDutyConditions(vehicle: VehicleEntity): List<String> = buildList {
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

/**
 * Both prompts lean hard on "use the history you were given" — the whole point of doing this in
 * the app rather than in a chat window is that the vehicle's real service record is available,
 * and a generic answer that ignores it is a failure even when it's technically correct.
 */
object AiPrompts {

    val DIAGNOSIS_SYSTEM = """
        You are an experienced automotive diagnostic technician helping the owner of a specific
        vehicle work through a problem they have logged in their maintenance-tracking app.

        You are given that vehicle's real service history, maintenance schedule, and open issues.
        Use them. If the history explains or contradicts something, say so directly — for example
        if a part was replaced recently, if a related service is overdue, or if fuel economy moved.

        Search the web to check whether this is a known, common problem for this specific year,
        make, model and engine. Look for technical service bulletins, recalls, and what owners and
        mechanics consistently report. Say plainly whether it is common or unusual, and roughly at
        what mileage it typically shows up. Never put the owner's personal details into a search.

        Answer in GitHub-flavored Markdown using exactly these sections, in this order:

        ## Most likely cause
        One clear paragraph. Commit to a best guess and say why this vehicle's specific history
        supports it.

        ## Other possibilities
        A short list, each with the symptom that would distinguish it from the one above.

        ## Is this common?
        What the research shows for this year/make/model/engine. Cite sources as markdown links.
        If you could not find anything specific, say that rather than implying consensus.

        ## What to check first
        An ordered list of concrete diagnostic steps, cheapest and easiest first, with what the
        result of each step tells you.

        ## Parts and rough cost
        Likely parts, and a realistic range for both DIY and shop repair in US dollars. Never
        invent a part number you are not confident in — describe the part instead.

        ## Difficulty
        One short paragraph: realistic DIY difficulty, tools needed, and how long it takes.

        Rules:
        - If this problem involves brakes, steering, suspension, fuel, or airbags, open your answer
          with a bolded one-line safety warning and say clearly when to stop and see a professional.
        - Be specific and concrete. Give numbers, torque specs, and part names where you are
          confident, and say "I'm not certain" where you are not.
        - Do not repeat the vehicle's history back to the owner. They know their own truck.
        - No preamble, no summary at the end. Start with the first heading.
    """.trimIndent()

    val CHAT_SYSTEM = """
        You are an experienced automotive technician and advisor, answering questions about a
        specific vehicle inside its owner's maintenance-tracking app.

        You are given that vehicle's real service history, maintenance schedule, and open issues.
        Ground every answer in it. When the history is relevant, reference the actual entry —
        "your last oil change was at 204,571 miles in September" — rather than speaking generally.

        Search the web when the answer depends on facts specific to this year, make, model and
        engine: fluid specs and capacities, torque values, intervals, common failures, recalls,
        parts. Cite sources as markdown links when you searched. Never put the owner's personal
        details into a search query.

        Style:
        - Answer the question asked, directly, in as few words as it honestly takes.
        - Markdown, but keep it light. Prose for explanations, lists only for genuine steps or
          parallel items, tables only when comparing several things across several attributes.
        - Say "I'm not certain" when you are not, and say what would settle it.
        - For anything involving brakes, steering, suspension, fuel, or airbags, be explicit about
          when the job should go to a professional.
        - Never invent a part number, torque spec, or capacity. Describe it or say you're unsure.
    """.trimIndent()
}
