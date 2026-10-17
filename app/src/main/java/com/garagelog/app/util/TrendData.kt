package com.garagelog.app.util

import com.garagelog.app.data.entity.LogEntryEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// The numbers behind the Trends tab, shared with the vehicle report so the two always agree.

/** Odometer readings over time, oldest first: (date, miles). */
fun odometerPoints(logs: List<LogEntryEntity>): List<Pair<String, Float>> =
    logs.filter { it.mileage != null }.sortedBy { it.date }.map { it.date to it.mileage!!.toFloat() }

/** Distinct calendar months with activity — the denominator for a per-month average. */
fun monthsSpanned(logs: List<LogEntryEntity>): Int =
    logs.filter { it.date.length >= 7 }.map { it.date.substring(0, 7) }.distinct().size

/** Spend per calendar month (yyyy-MM to dollars), oldest first, the most recent [lastMonths]. */
fun monthlyTotals(logs: List<LogEntryEntity>, lastMonths: Int = 6): List<Pair<String, Double>> =
    logs.filter { it.date.length >= 7 }
        .groupBy { it.date.substring(0, 7) }
        .mapValues { (_, entries) -> entries.sumOf { it.cost ?: 0.0 } }
        .toSortedMap()
        .toList()
        .takeLast(lastMonths)

/** "Sep" for "2026-09". */
fun monthLabel(yyyyMm: String): String {
    val parts = yyyyMm.split("-")
    if (parts.size != 2) return yyyyMm
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yyyyMm
    return monthName(monthIndex).take(3)
}

/** Spend by log category, largest first. */
fun categoryTotals(logs: List<LogEntryEntity>): List<Pair<String, Double>> =
    logs.groupBy { it.category }
        .mapValues { (_, entries) -> entries.sumOf { it.cost ?: 0.0 } }
        .toList()
        .sortedByDescending { it.second }

/**
 * Whole dollars, and thousands as "$1.2k": six bars leave each label about 45dp, and "$1,203.27"
 * wrapped onto two lines there. The exact totals are stated beside the chart.
 */
fun compactMoney(value: Float): String = when {
    value >= 1000f -> "$" + String.format(Locale.US, "%.1fk", value / 1000f).replace(".0k", "k")
    else -> "$" + value.roundToInt()
}

/**
 * Three to five round tick values (steps of 1, 2, 2.5 or 5 times a power of ten) that bracket
 * [min]..[max]. A flat series still gets a real span around its value rather than a zero-height
 * axis, so the line sits in the middle at its true reading.
 */
fun niceTicks(min: Float, max: Float): List<Float> {
    val span = (max - min).takeIf { it > 0f } ?: maxOf(abs(max) * 0.1f, 1f)
    val raw = span / 3f
    val magnitude = 10f.pow(floor(log10(raw)))
    val step = when (raw / magnitude) {
        in 0f..1f -> 1f
        in 1f..2f -> 2f
        in 2f..2.5f -> 2.5f
        in 2.5f..5f -> 5f
        else -> 10f
    } * magnitude
    val center = (min + max) / 2f
    val lo = if (max > min) floor(min / step) * step else floor((center - span / 2f) / step) * step
    val hi = if (max > min) ceil(max / step) * step else ceil((center + span / 2f) / step) * step
    return generateSequence(lo) { it + step }.takeWhile { it <= hi + step / 2f }.toList()
}

/** "18", "17.5", or "205k" for odometer-sized values — only as many decimals as the step needs. */
fun formatTick(value: Float, step: Float): String {
    if (abs(value) >= 10_000f && step >= 1_000f) {
        val decimals = if (step % 1_000f == 0f) 0 else 1
        return "%,.${decimals}fk".format(value / 1_000f)
    }
    val decimals = when {
        step >= 1f -> if (step % 1f == 0f) 0 else 1
        else -> (-floor(log10(step))).toInt().coerceAtMost(3)
    }
    return "%,.${decimals}f".format(value)
}
