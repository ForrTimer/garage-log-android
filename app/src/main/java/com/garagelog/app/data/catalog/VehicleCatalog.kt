package com.garagelog.app.data.catalog

import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** One engine/transmission combination fueleconomy.gov lists for a year/make/model. */
data class CatalogConfig(val label: String, val id: String)

/**
 * What a catalog lookup or VIN decode learned about a vehicle. Every field is optional: the form
 * only overwrites what the source actually knew, so a decode never blanks something typed by hand.
 */
data class CatalogSpec(
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val trim: String? = null,
    val bodyStyle: String? = null,
    val transmissionType: String? = null,
    val transmissionSpeeds: Int? = null,
    val cylinders: Int? = null,
    val displacementL: Double? = null,
    val fuelType: String? = null,
    val aspiration: String? = null,
    val drivetrain: String? = null,
)

/**
 * Year → make → model → configuration lookups for the vehicle form, from two free, keyless US
 * government sources:
 *
 * - **fueleconomy.gov** is the main one: its menus are already filtered by year, and a
 *   configuration carries transmission, cylinders, displacement, fuel and drivetrain. It only
 *   covers vehicles EPA rates, so heavy-duty trucks (a 2500HD, a K2500 Suburban) aren't in it.
 * - **NHTSA vPIC** fills that gap for model names, and decodes VINs.
 *
 * Every call degrades to an empty result rather than throwing: offline, or a model neither source
 * knows, the form still takes whatever is typed. Results are cached for the process's lifetime —
 * the data changes yearly, and a form session asks the same questions repeatedly as you type.
 */
class VehicleCatalog(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Any>()

    /** Newest first; next model year included since they go on sale the year before. */
    fun years(): List<Int> {
        val next = Calendar.getInstance().get(Calendar.YEAR) + 1
        return (next downTo 1950).toList()
    }

    // Pre-1984 isn't in fueleconomy.gov at all (and offline returns nothing); a fixed list beats an
    // empty dropdown. Applied outside the cache so an offline miss isn't remembered as the answer.
    suspend fun makes(year: Int): List<String> =
        cached("makes/$year") { menu("$FE/vehicle/menu/make?year=$year").map { it.first } }
            .ifEmpty { COMMON_MAKES }

    /** fueleconomy.gov's model names (which encode drivetrain, e.g. "Tacoma 4WD") plus NHTSA's. */
    suspend fun models(year: Int, make: String): List<String> = cached("models/$year/${make.lowercase()}") {
        val epa = epaModels(year, make)
        val nhtsa = runCatching {
            val root = getJson("$VPIC/GetModelsForMakeYear/make/${enc(make)}/modelyear/$year?format=json")
            (root?.jsonObject?.get("Results") as? JsonArray).orEmpty()
                .mapNotNull { it.jsonObject["Model_Name"]?.stringOrNull()?.trim() }
        }.getOrDefault(emptyList())
        (epa + nhtsa).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    /**
     * Engine/transmission choices for what's been typed so far. A bare "Tacoma" matches both
     * "Tacoma 2WD" and "Tacoma 4WD", so the list narrows as the model gets more specific instead of
     * showing nothing until the exact EPA name is typed.
     */
    suspend fun configurations(year: Int, make: String, model: String): List<CatalogConfig> {
        val typed = model.trim().lowercase()
        if (typed.isEmpty()) return emptyList()
        val matches = epaModels(year, make).filter {
            val m = it.lowercase()
            m == typed || m.startsWith("$typed ")
        }.take(MAX_MODEL_VARIANTS)
        return matches.flatMap { epaModel ->
            cached("configs/$year/$make/$epaModel") {
                menu("$FE/vehicle/menu/options?year=$year&make=${enc(make)}&model=${enc(epaModel)}")
                    .map { (text, id) -> CatalogConfig(if (matches.size > 1) "$epaModel · $text" else text, id) }
            }
        }
    }

    suspend fun configurationSpec(id: String): CatalogSpec? = runCatching {
        val o = getJson("$FE/vehicle/$id")?.jsonObject ?: return null
        val atv = o.str("atvType")
        CatalogSpec(
            model = o.str("baseModel"),
            transmissionType = transmissionType(o.str("trany")),
            transmissionSpeeds = o.str("trany")?.let { SPEEDS.find(it)?.groupValues?.drop(1)?.firstOrNull { g -> g.isNotEmpty() }?.toIntOrNull() },
            cylinders = o.str("cylinders")?.toIntOrNull(),
            displacementL = o.str("displ")?.toDoubleOrNull(),
            fuelType = when {
                atv.equals("EV", true) -> "Electric"
                atv.equals("Plug-in Hybrid", true) -> "Plug-in hybrid"
                atv.equals("Hybrid", true) -> "Hybrid"
                atv.equals("FFV", true) -> "Flex fuel"
                atv.equals("Diesel", true) -> "Diesel"
                else -> fuelType(o.str("fuelType1") ?: o.str("fuelType"))
            },
            aspiration = when {
                o.str("tCharger") == "T" -> "Turbocharged"
                o.str("sCharger") == "S" -> "Supercharged"
                o.str("cylinders") != null -> "Naturally aspirated"
                else -> null
            },
            drivetrain = drivetrain(o.str("drive")),
            bodyStyle = bodyFromEpaClass(o.str("VClass")),
        )
    }.getOrNull()

    /** Null when offline or the VIN is unreadable; partial when NHTSA only knew some of it. */
    suspend fun decodeVin(vin: String): CatalogSpec? = runCatching {
        val results = getJson("$VPIC/DecodeVinValues/${enc(vin.trim())}?format=json")
            ?.jsonObject?.get("Results") as? JsonArray ?: return null
        val r = results.firstOrNull()?.jsonObject ?: return null
        val electrification = r.str("ElectrificationLevel").orEmpty()
        CatalogSpec(
            year = r.str("ModelYear")?.toIntOrNull(),
            make = r.str("Make")?.let(::tidyMakeCase),
            model = r.str("Model"),
            trim = r.str("Trim") ?: r.str("Series"),
            bodyStyle = bodyFromNhtsa(r.str("BodyClass")),
            transmissionType = r.str("TransmissionStyle")?.let {
                when {
                    it.contains("CVT", true) || it.contains("Continuously", true) -> "CVT"
                    it.contains("Manual", true) -> "Manual"
                    else -> "Automatic"
                }
            },
            transmissionSpeeds = r.str("TransmissionSpeeds")?.toIntOrNull(),
            cylinders = r.str("EngineCylinders")?.toIntOrNull(),
            displacementL = r.str("DisplacementL")?.toDoubleOrNull()?.let { Math.round(it * 10) / 10.0 },
            fuelType = when {
                electrification.startsWith("BEV", true) -> "Electric"
                electrification.startsWith("PHEV", true) -> "Plug-in hybrid"
                electrification.contains("HEV", true) -> "Hybrid"
                else -> fuelType(r.str("FuelTypePrimary"))
            },
            aspiration = if (r.str("Turbo").equals("Yes", true)) "Turbocharged" else null,
            drivetrain = drivetrain(r.str("DriveType")),
        ).takeIf { it.make != null || it.model != null || it.year != null }
    }.getOrNull()

    private suspend fun epaModels(year: Int, make: String): List<String> = cached("epaModels/$year/${make.lowercase()}") {
        menu("$FE/vehicle/menu/model?year=$year&make=${enc(make)}").map { it.first }
    }

    /**
     * fueleconomy.gov menus: a list normally, but a bare object when there's exactly one entry and
     * the literal `null` when there are none — all three have to parse.
     */
    private suspend fun menu(url: String): List<Pair<String, String>> {
        val item = runCatching { getJson(url)?.jsonObject?.get("menuItem") }.getOrNull() ?: return emptyList()
        val entries = when (item) {
            is JsonArray -> item.toList()
            is JsonObject -> listOf(item)
            else -> emptyList()
        }
        return entries.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val text = o.str("text") ?: return@mapNotNull null
            text to (o.str("value") ?: text)
        }
    }

    private suspend fun getJson(url: String): JsonElement? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string().trim()
                if (body.isEmpty() || body == "null") null else json.parseToJsonElement(body)
            }
        } catch (e: IOException) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        cache[key]?.let { return it as T }
        val value = load()
        // Don't cache an empty answer — it's as likely to mean "offline right now" as "none exist".
        if (value !is Collection<*> || value.isNotEmpty()) cache[key] = value
        return value
    }

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")

    private companion object {
        const val FE = "https://www.fueleconomy.gov/ws/rest"
        const val VPIC = "https://vpic.nhtsa.dot.gov/api/vehicles"
        const val MAX_MODEL_VARIANTS = 6

        /** "Manual 6-spd" → 6, "Automatic (S6)" → 6, "Automatic (AV-S7)" → 7. */
        val SPEEDS = Regex("""(\d+)-spd|(\d+)\)""")

        val COMMON_MAKES = listOf(
            "AMC", "Buick", "Cadillac", "Chevrolet", "Chrysler", "Datsun", "Dodge", "Ford", "GMC",
            "Honda", "International", "Jeep", "Lincoln", "Mazda", "Mercedes-Benz", "Mercury",
            "Nissan", "Oldsmobile", "Plymouth", "Pontiac", "Porsche", "Subaru", "Toyota",
            "Volkswagen", "Volvo",
        )

        fun transmissionType(trany: String?): String? = when {
            trany == null -> null
            trany.contains("Manual", true) -> "Manual"
            trany.contains("(AV", true) || trany.contains("variable", true) -> "CVT"
            else -> "Automatic"
        }

        fun fuelType(raw: String?): String? = when {
            raw == null -> null
            raw.contains("Diesel", true) -> "Diesel"
            raw.contains("Electric", true) -> "Electric"
            raw.contains("E85", true) || raw.contains("Flex", true) -> "Flex fuel"
            raw.contains("Natural Gas", true) || raw.contains("CNG", true) -> "CNG"
            raw.contains("Gas", true) || raw.contains("Regular", true) || raw.contains("Premium", true) ||
                raw.contains("Midgrade", true) -> "Gasoline"
            else -> null
        }

        fun drivetrain(raw: String?): String? = when {
            raw == null -> null
            raw.contains("4WD", true) || raw.contains("4-Wheel", true) || raw.contains("4x4", true) -> "4WD"
            raw.contains("AWD", true) || raw.contains("All-Wheel", true) -> "AWD"
            raw.contains("Front", true) || raw.contains("FWD", true) -> "FWD"
            raw.contains("Rear", true) || raw.contains("RWD", true) || raw.contains("4x2", true) -> "RWD"
            else -> null
        }

        /** Only the classes that name a body unambiguously; "Midsize Cars" could be a sedan or a coupe. */
        fun bodyFromEpaClass(vClass: String?): String? = when {
            vClass == null -> null
            vClass.contains("Pickup", true) -> "Pickup"
            vClass.contains("Sport Utility", true) -> "SUV"
            vClass.contains("Minivan", true) -> "Minivan"
            vClass.contains("Van", true) -> "Van"
            vClass.contains("Wagon", true) -> "Wagon"
            else -> null
        }

        fun bodyFromNhtsa(raw: String?): String? = when {
            raw == null -> null
            raw.contains("Pickup", true) -> "Pickup"
            raw.contains("SUV", true) || raw.contains("Sport Utility", true) -> "SUV"
            raw.contains("Minivan", true) -> "Minivan"
            raw.contains("Van", true) -> "Van"
            raw.contains("Convertible", true) -> "Convertible"
            raw.contains("Coupe", true) -> "Coupe"
            raw.contains("Hatchback", true) -> "Hatchback"
            raw.contains("Wagon", true) -> "Wagon"
            raw.contains("Sedan", true) -> "Sedan"
            else -> null
        }

        /** NHTSA shouts ("CHEVROLET", "LAND ROVER"); short acronyms like GMC/BMW stay as they are. */
        fun tidyMakeCase(make: String): String =
            if (make != make.uppercase()) make
            else make.split(" ").joinToString(" ") { word ->
                if (word.length <= 3) word
                else word.split("-").joinToString("-") { it.lowercase().replaceFirstChar(Char::uppercase) }
            }

        fun JsonObject.str(key: String): String? = this[key]?.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "Not Applicable" }

        fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
