package com.garagelog.app.util

import com.garagelog.app.data.entity.VehicleEntity

/**
 * A vehicle's filled-in details as label/value pairs, in the order a person reads a spec sheet.
 * Blank fields are left out rather than shown as "Trim: —", so a sparse vehicle stays short.
 * Shared by the Home card's details and the assistant's vehicle profile, so both describe a
 * vehicle the same way.
 */
fun vehicleSpecLines(v: VehicleEntity, includePersonal: Boolean = true): List<Pair<String, String>> = buildList {
    fun add(label: String, value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { add(label to it) }
    }
    add("Year", v.year?.toString())
    add("Make", v.make)
    add("Model", v.model)
    add("Trim", v.trim)
    add("Body", v.bodyStyle)
    add("Cab", v.cabStyle)
    add("Bed", v.bedLength)
    add("Engine", v.engine)
    add("Displacement", v.displacementL?.let { "${formatLiters(it)} L" })
    add("Cylinders", v.cylinders?.toString())
    add("Aspiration", v.aspiration)
    add("Fuel", v.fuelType)
    add("Transmission", transmissionLabel(v))
    add("Drivetrain", v.drivetrain)
    if (includePersonal) {
        add("VIN", v.vin)
        add("Color", v.color)
        add("Role", v.role)
        add("Notes", v.notes)
    }
}

/** "Automatic, 6-speed" / "Manual" / "6-speed" — whichever parts are known. */
fun transmissionLabel(v: VehicleEntity): String? =
    listOfNotNull(v.transmissionType.ifBlank { null }, v.transmissionSpeeds?.let { "$it-speed" })
        .joinToString(", ")
        .ifBlank { null }

/** 6.0 → "6.0", 2.35 → "2.35": keeps the conventional one decimal without inventing precision. */
fun formatLiters(liters: Double): String =
    if (liters * 10 % 1.0 == 0.0) "%.1f".format(liters) else liters.toString()
