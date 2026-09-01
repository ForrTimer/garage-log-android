package com.garagelog.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
// All our ISO date strings are calendar dates with no real time-of-day meaning, parsed/formatted
// as UTC midnight throughout (matching Material3's DatePicker convention) — prettyFormat has to
// share that timezone too, or a date parsed as UTC and then displayed in the local zone can drift
// onto the adjacent day depending on the device's offset.
private val prettyFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun todayIso(): String = isoFormat.format(Date())

fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val parsed = isoFormat.parse(iso) ?: return iso
        prettyFormat.format(parsed)
    } catch (e: Exception) {
        iso
    }
}

fun formatMoney(value: Double?): String {
    if (value == null) return "—"
    val nf = NumberFormat.getCurrencyInstance(Locale.US)
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

fun formatMiles(value: Int?): String {
    if (value == null) return "—"
    return "${NumberFormat.getIntegerInstance(Locale.US).format(value)} mi"
}

/** Adds [months] calendar months to an ISO yyyy-MM-dd date string. */
fun addMonthsToIso(iso: String, months: Int): String? {
    return try {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = isoFormat.parse(iso) ?: return null
        cal.add(java.util.Calendar.MONTH, months)
        isoFormat.format(cal.time)
    } catch (e: Exception) {
        null
    }
}

/** True if [iso] (yyyy-MM-dd) is today or in the past. */
fun isIsoDateOnOrBeforeToday(iso: String): Boolean = iso <= todayIso()

/** UTC-midnight millis for a yyyy-MM-dd string, matching Material3 DatePicker's convention. */
fun isoToUtcMillis(iso: String): Long? = if (iso.isBlank()) null else try { isoFormat.parse(iso)?.time } catch (e: Exception) { null }

fun utcMillisToIso(millis: Long): String = isoFormat.format(Date(millis))

private val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)

/** Human-readable local timestamp, for "last synced at" display. */
fun formatDateTime(millis: Long?): String {
    if (millis == null) return "never"
    return timeFormat.format(Date(millis))
}
