package com.garagelog.app.util

import com.garagelog.app.data.entity.MaintenanceScheduleEntity

enum class DueStatus { OK, DUE_SOON, OVERDUE, UNKNOWN }

data class ScheduleDueInfo(val status: DueStatus, val label: String)

private const val DUE_SOON_MILES_WINDOW = 500

/** Pure due/overdue computation shared by the Dashboard badges and the Schedule screen. */
fun computeDueInfo(schedule: MaintenanceScheduleEntity, currentMiles: Int?): ScheduleDueInfo {
    val mileageRemaining: Int? =
        if (schedule.intervalMiles != null && schedule.lastDoneMileage != null && currentMiles != null) {
            (schedule.lastDoneMileage + schedule.intervalMiles) - currentMiles
        } else null

    val dueDate: String? = if (schedule.intervalMonths != null && schedule.lastDoneDate != null) {
        addMonthsToIso(schedule.lastDoneDate, schedule.intervalMonths)
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
