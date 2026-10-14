package com.garagelog.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.garagelog.app.data.catalog.CatalogSpec
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.util.formatLiters

/** Everything editable, as the text being typed — parsed into numbers only at Save. */
internal data class VehicleFormState(
    val name: String,
    val year: String,
    val make: String,
    val model: String,
    val trim: String,
    val engine: String,
    val displacementL: String,
    val cylinders: String,
    val aspiration: String,
    val fuelType: String,
    val transmissionType: String,
    val transmissionSpeeds: String,
    val drivetrain: String,
    val bodyStyle: String,
    val cabStyle: String,
    val bedLength: String,
    val vin: String,
    val color: String,
    val miles: String,
    val role: String,
    val notes: String,
    val severeDustyAreas: Boolean,
    val severeTowing: Boolean,
    val severeExtendedIdling: Boolean,
    val severeLowSpeedColdWeather: Boolean,
    val severeHeavyCityTrafficHot: Boolean,
    val severeMountainousHot: Boolean,
    val severeFrequentTowing: Boolean,
    val severeDeepWater: Boolean,
) {
    val isPickup get() = bodyStyle.equals("Pickup", ignoreCase = true)
    val isElectric get() = fuelType.equals("Electric", ignoreCase = true)

    /** Overwrites only what [spec] actually knew, so a lookup never blanks a typed value. */
    fun apply(spec: CatalogSpec): VehicleFormState = copy(
        year = spec.year?.toString() ?: year,
        make = spec.make ?: make,
        model = spec.model ?: model,
        trim = spec.trim ?: trim,
        bodyStyle = spec.bodyStyle ?: bodyStyle,
        transmissionType = spec.transmissionType ?: transmissionType,
        transmissionSpeeds = spec.transmissionSpeeds?.toString() ?: transmissionSpeeds,
        cylinders = spec.cylinders?.toString() ?: cylinders,
        displacementL = spec.displacementL?.let(::formatLiters) ?: displacementL,
        fuelType = spec.fuelType ?: fuelType,
        aspiration = spec.aspiration ?: aspiration,
        drivetrain = spec.drivetrain ?: drivetrain,
    )

    companion object {
        fun from(v: VehicleEntity?) = VehicleFormState(
            name = v?.name ?: "",
            year = v?.year?.toString() ?: "",
            make = v?.make ?: "",
            model = v?.model ?: "",
            trim = v?.trim ?: "",
            engine = v?.engine ?: "",
            displacementL = v?.displacementL?.let(::formatLiters) ?: "",
            cylinders = v?.cylinders?.toString() ?: "",
            aspiration = v?.aspiration ?: "",
            fuelType = v?.fuelType ?: "",
            transmissionType = v?.transmissionType ?: "",
            transmissionSpeeds = v?.transmissionSpeeds?.toString() ?: "",
            drivetrain = normalizeDrivetrain(v?.drivetrain ?: ""),
            bodyStyle = v?.bodyStyle ?: "",
            cabStyle = v?.cabStyle ?: "",
            bedLength = v?.bedLength ?: "",
            vin = v?.vin ?: "",
            color = v?.color ?: "",
            miles = v?.miles?.toString() ?: "",
            role = v?.role ?: "",
            notes = v?.notes ?: "",
            severeDustyAreas = v?.severeDustyAreas ?: false,
            severeTowing = v?.severeTowing ?: false,
            severeExtendedIdling = v?.severeExtendedIdling ?: false,
            severeLowSpeedColdWeather = v?.severeLowSpeedColdWeather ?: false,
            severeHeavyCityTrafficHot = v?.severeHeavyCityTrafficHot ?: false,
            severeMountainousHot = v?.severeMountainousHot ?: false,
            severeFrequentTowing = v?.severeFrequentTowing ?: false,
            severeDeepWater = v?.severeDeepWater ?: false,
        )
    }
}

internal val TRANSMISSION_TYPES = listOf("Automatic", "Manual", "CVT")
internal val DRIVETRAINS = listOf("RWD", "FWD", "AWD", "4WD")
internal val BODY_STYLES = listOf("Pickup", "SUV", "Sedan", "Coupe", "Hatchback", "Wagon", "Minivan", "Van", "Convertible")
internal val CAB_STYLES = listOf("Regular cab", "Extended cab", "Crew cab")
internal val BED_LENGTHS = listOf("Short bed", "Standard bed", "Long bed")
internal val FUEL_TYPES = listOf("Gasoline", "Diesel", "Hybrid", "Plug-in hybrid", "Electric", "Flex fuel", "CNG")
internal val ASPIRATIONS = listOf("Naturally aspirated", "Turbocharged", "Twin-turbo", "Supercharged")
internal val CYLINDERS = listOf("3", "4", "5", "6", "8", "10", "12")

/** Manuals top out around 7 speeds; modern automatics run to 10. */
internal fun speedOptions(type: String): List<String> = when (type) {
    "Manual" -> (3..7).map { it.toString() }
    "CVT" -> emptyList()
    else -> (3..10).map { it.toString() }
}

/** Older entries were free text ("4x4"); map the common spellings onto the picker's options. */
internal fun normalizeDrivetrain(raw: String): String = when (raw.trim().lowercase()) {
    "4x4", "4wd", "four wheel drive" -> "4WD"
    "4x2", "2wd", "rwd" -> "RWD"
    "fwd" -> "FWD"
    "awd" -> "AWD"
    else -> raw.trim()
}

