package com.garagelog.app.util

import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/**
 * How fast a vehicle is actually being driven, measured from its own odometer history rather than
 * assumed. [basisDays] and [basisMiles] are carried so the UI can say what the rate is based on —
 * a rate from two readings a week apart deserves less trust than one from a year of entries.
 */
data class DrivingRate(val milesPerDay: Double, val basisDays: Int, val basisMiles: Int, val readings: Int)

/**
 * Needs at least two dated odometer readings spanning a real interval. Returns null rather than
 * guessing — a vehicle with one reading (or several on the same day) has no measurable rate, and
 * inventing one would put a confident, wrong date on every projected service.
 */
fun drivingRate(logs: List<LogEntryEntity>): DrivingRate? {
    val readings = logs.filter { it.mileage != null && it.date.length >= 10 }
        .sortedBy { it.date }
    if (readings.size < 2) return null
    val first = readings.first()
    val last = readings.last()
    val miles = (last.mileage ?: return null) - (first.mileage ?: return null)
    val days = daysBetweenIso(first.date, last.date) ?: return null
    if (days <= 0 || miles <= 0) return null
    return DrivingRate(
        milesPerDay = miles.toDouble() / days,
        basisDays = days,
        basisMiles = miles,
        readings = readings.size,
    )
}

/**
 * A scheduled service placed on the calendar: whichever comes first out of "when the miles run
 * out at the current rate" and "when the time interval expires".
 *
 * [projectedIso] is null when neither could be worked out — no driving rate, or no interval.
 */
data class ProjectedService(
    val schedule: MaintenanceScheduleEntity,
    val due: ScheduleDueInfo,
    val projectedIso: String?,
    val daysAway: Int?,
) {
    val isOverdue: Boolean get() = due.status == DueStatus.OVERDUE
}

fun projectSchedules(
    schedules: List<MaintenanceScheduleEntity>,
    currentMiles: Int?,
    severeDuty: Boolean,
    rate: DrivingRate?,
): List<ProjectedService> {
    val today = todayIso()
    return schedules.map { schedule ->
        val due = computeDueInfo(schedule, currentMiles, severeDuty)

        val byMileage = due.remainingMiles
            ?.takeIf { rate != null && rate.milesPerDay > 0 }
            ?.let { remaining ->
                addDaysToIso(today, (remaining / rate!!.milesPerDay).roundToInt().coerceAtLeast(0))
            }

        val byCalendar = schedule.lastDoneDate?.let { lastDone ->
            schedule.intervalMonths
                ?.let { if (severeDuty) maxOf(1, it / 2) else it }
                ?.let { addMonthsToIso(lastDone, it) }
        }

        // Whichever limit is hit first is the one that actually falls due.
        val projected = listOfNotNull(byMileage, byCalendar).minOrNull()
        ProjectedService(
            schedule = schedule,
            due = due,
            projectedIso = projected,
            daysAway = projected?.let { daysBetweenIso(today, it) },
        )
    }.sortedWith(
        // Overdue first, then soonest projected; anything unprojectable sinks to the bottom rather
        // than sorting as "due now" off a null.
        compareByDescending<ProjectedService> { it.isOverdue }
            .thenBy { it.daysAway ?: Int.MAX_VALUE }
            .thenBy { it.schedule.taskName },
    )
}

/** Whole days from [startIso] to [endIso]; negative when [endIso] is in the past. */
fun daysBetweenIso(startIso: String, endIso: String): Int? {
    val start = runCatching { iso.parse(startIso) }.getOrNull() ?: return null
    val end = runCatching { iso.parse(endIso) }.getOrNull() ?: return null
    // Round rather than truncate so a DST-shortened day doesn't lose a whole day.
    return ((end.time - start.time).toDouble() / 86_400_000.0).roundToInt()
}

fun addDaysToIso(isoDate: String, days: Int): String? {
    val parsed = runCatching { iso.parse(isoDate) }.getOrNull() ?: return null
    val calendar = Calendar.getInstance().apply {
        time = parsed
        add(Calendar.DAY_OF_MONTH, days)
    }
    return iso.format(calendar.time)
}
