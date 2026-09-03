package com.garagelog.app.util

/**
 * A starter list of common maintenance items with typical (conservative, "normal duty") OEM
 * intervals, offered as a checklist when adding a vehicle — see item 6 of the roadmap: rather
 * than the app trying to look up a real OEM schedule (no internet data source for that exists
 * in this app), the owner picks which services apply and gets a sane default interval to edit.
 */
data class CommonServiceTemplate(val name: String, val intervalMiles: Int?, val intervalMonths: Int?)

object CommonMaintenanceServices {
    val all: List<CommonServiceTemplate> = listOf(
        CommonServiceTemplate("Oil change", 5000, 6),
        CommonServiceTemplate("Tire rotation", 6000, 6),
        CommonServiceTemplate("Brake fluid flush", 30000, 24),
        CommonServiceTemplate("Coolant flush", 30000, 36),
        CommonServiceTemplate("Transmission fluid service", 30000, 36),
        CommonServiceTemplate("Spark plug replacement", 30000, null),
        CommonServiceTemplate("Engine air filter", 15000, 12),
        CommonServiceTemplate("Cabin air filter", 15000, 12),
        CommonServiceTemplate("Timing belt", 60000, 60),
        CommonServiceTemplate("Differential fluid service", 30000, 24),
        CommonServiceTemplate("Battery inspection", null, 12),
        CommonServiceTemplate("Wheel alignment", null, 12),
    )

    val byName: Map<String, CommonServiceTemplate> = all.associateBy { it.name }

    /** Pre-checked by default when adding a new vehicle — the two services every owner tracks. */
    val defaultSelected: Set<String> = setOf("Oil change", "Tire rotation")
}
