package com.garagelog.app.util

import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity

/**
 * MPG is only trustworthy between two consecutive *full-tank* fill-ups — a partial fill leaves
 * gas in the tank that the math has no way to account for. Gallons from any partial fills in
 * between still count (that fuel was burned too), they just don't get their own MPG point.
 */
data class FillUp(val date: String, val mpg: Float)

fun fillUpMpg(logs: List<LogEntryEntity>): List<FillUp> {
    val fuelLogs = logs.filter { it.category == LogCategory.Fuel.name && it.mileage != null && it.gallons != null }
        .sortedBy { it.mileage }
    val result = mutableListOf<FillUp>()
    var lastFullTank: LogEntryEntity? = null
    var gallonsSinceLastFullTank = 0.0
    for (entry in fuelLogs) {
        gallonsSinceLastFullTank += entry.gallons ?: 0.0
        if (entry.fullTank) {
            val previous = lastFullTank
            if (previous != null && previous.mileage != null && entry.mileage != null && gallonsSinceLastFullTank > 0) {
                val miles = entry.mileage - previous.mileage
                if (miles > 0) result.add(FillUp(entry.date, (miles / gallonsSinceLastFullTank).toFloat()))
            }
            lastFullTank = entry
            gallonsSinceLastFullTank = 0.0
        }
    }
    return result
}
