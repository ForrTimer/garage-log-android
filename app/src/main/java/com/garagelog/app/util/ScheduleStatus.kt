package com.garagelog.app.util

import com.garagelog.app.data.entity.MaintenanceScheduleEntity

enum class DueStatus { OK, DUE_SOON, OVERDUE, UNKNOWN }

data class ScheduleDueInfo(val status: DueStatus, val label: String)

private const val DUE_SOON_MILES_WINDOW = 500

/**
 * Pure due/overdue computation shared by the Dashboard badges and the Schedule screen.
 * [severeDuty] halves both intervals, matching the "perform at half the indicated interval"
 * caveat most OEM manuals attach to dusty/towing/extended-idling/extreme-temperature use.
 */
fun computeDueInfo(schedule: MaintenanceScheduleEntity, currentMiles: Int?, severeDuty: Boolean = false): ScheduleDueInfo {
    val effectiveIntervalMiles = schedule.intervalMiles?.let { if (severeDuty) it / 2 else it }
    val effectiveIntervalMonths = schedule.intervalMonths?.let { if (severeDuty) maxOf(1, it / 2) else it }

    val mileageRemaining: Int? =
        if (effectiveIntervalMiles != null && schedule.lastDoneMileage != null && currentMiles != null) {
            (schedule.lastDoneMileage + effectiveIntervalMiles) - currentMiles
        } else null

    val dueDate: String? = if (effectiveIntervalMonths != null && schedule.lastDoneDate != null) {
        addMonthsToIso(schedule.lastDoneDate, effectiveIntervalMonths)
    } else null

    val dateOverdue = dueDate != null && isIsoDateOnOrBeforeToday(dueDate)
    val mileageOverdue = mileageRemaining != null && mileageRemaining <= 0
    val mileageDueSoon = mileageRemaining != null && mileageRemaining in 1..DUE_SOON_MILES_WINDOW

    return when {
        mileageOverdue || dateOverdue -> ScheduleDueInfo(
            DueStatus.OVERDUE,
            buildString {
                if (mileageOverdue) append("Overdue by ${-mileageRemaining!!} mi")
                if (mileageOverdue && dateOverdue) append(" · ")
                if (dateOverdue) append("overdue since ${formatDate(dueDate)}")
            },
        )
        mileageDueSoon -> ScheduleDueInfo(DueStatus.DUE_SOON, "Due in $mileageRemaining mi")
        mileageRemaining != null -> ScheduleDueInfo(
            DueStatus.OK,
            "Due in $mileageRemaining mi" + (dueDate?.let { " (or by ${formatDate(it)})" } ?: ""),
        )
        dueDate != null -> ScheduleDueInfo(DueStatus.OK, "Due by ${formatDate(dueDate)}")
        schedule.intervalMiles == null && schedule.intervalMonths == null ->
            ScheduleDueInfo(DueStatus.UNKNOWN, "No interval set")
        else -> ScheduleDueInfo(DueStatus.UNKNOWN, "No service history yet — mark done once to start tracking")
    }
}
