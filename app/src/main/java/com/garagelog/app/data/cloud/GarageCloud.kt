package com.garagelog.app.data.cloud

import android.content.Context
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.data.sync.SyncIssue
import com.garagelog.app.data.sync.SyncLog
import com.garagelog.app.data.sync.SyncSchedule
import com.garagelog.app.data.sync.SyncVehicle
import com.garagelog.app.data.sync.toSync
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Which shared garage this device works in, and as what. Null garageId = not set up yet. */
data class GarageMembership(val garageId: String?, val role: String?, val email: String?)

/** What a setup run did, with the cloud's own counts read back so the result is checked, not assumed. */
data class GarageSetupResult(
    val garageId: String,
    val created: Boolean,
    val uploaded: Boolean,
    val local: Map<String, Int>,
    val cloud: Map<String, Int>,
) {
    val matches: Boolean get() = !uploaded || local == cloud
}

/**
 * The shared-garage side of Firestore (see firebase/README.md for the model and
 * firebase/firestore.rules for who may do what).
 *
 * This step creates the owner's garage and uploads the device's records into it, one document per
 * row, keyed by the same ids Room uses and serialized through the same Sync* models the Drive sync
 * uses, so a document is exactly what Drive held for that row. Ongoing two-way sync comes next.
 */
class GarageCloud(
    context: Context,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true }

    private val _membership = MutableStateFlow(storedMembership())
    val membership: StateFlow<GarageMembership> = _membership.asStateFlow()

    /**
     * Finds this account's garage, or creates one with the owner as its admin, then uploads this
     * device's records if they haven't gone up yet. Safe to run again: every write is keyed by the
     * record's own id, so a re-run after a failure overwrites rather than duplicates.
     */
    suspend fun setUpGarage(): GarageSetupResult {
        val user = requireNotNull(auth.currentUser) { "Not signed in to Firebase." }
        val uid = user.uid
        val email = user.email.orEmpty().lowercase()

        val existing = (db.document("users/$uid").get().await().get("garageIds") as? List<*>)
            ?.filterIsInstance<String>()?.firstOrNull()
        val created = existing == null
        val garageId = existing ?: createGarage(uid, email, user.displayName)

        val role = db.document("garages/$garageId/members/$uid").get().await().getString("role")
            ?: error("This account isn't a member of its garage.")
        remember(garageId, role, email)

        // Only an admin's device seeds the garage, and only once: after that, sync owns the data.
        val shouldUpload = role == ROLE_ADMIN && !prefs.getBoolean(uploadedKey(garageId), false)
        val local = localCounts()
        if (shouldUpload) {
            upload(garageId)
            prefs.edit().putBoolean(uploadedKey(garageId), true).apply()
        }
        return GarageSetupResult(
            garageId = garageId,
            created = created,
            uploaded = shouldUpload,
            local = local,
            cloud = if (shouldUpload) cloudCounts(garageId) else emptyMap(),
        )
    }

    // The rules check each step against the one before (a member needs its garage, a vehicle needs
    // an admin member), so these are separate awaited writes, not one batch.
    private suspend fun createGarage(uid: String, email: String, displayName: String?): String {
        val garage = db.collection("garages").document()
        val now = System.currentTimeMillis()
        val firstName = displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        garage.set(
            mapOf(
                "name" to (firstName?.let { "$it's garage" } ?: "My garage"),
                "createdBy" to uid,
                "createdAt" to now,
                "updatedAt" to now,
            ),
        ).await()
        garage.collection("members").document(uid).set(
            mapOf(
                "role" to ROLE_ADMIN,
                "vehicleIds" to emptyList<String>(),
                "email" to email,
                "displayName" to displayName.orEmpty(),
                "updatedAt" to now,
            ),
        ).await()
        db.document("users/$uid").set(
            mapOf("email" to email, "displayName" to displayName.orEmpty(), "garageIds" to FieldValue.arrayUnion(garage.id)),
            SetOptions.merge(),
        ).await()
        return garage.id
    }

    private suspend fun upload(garageId: String) {
        // Tombstones go up too: a deleted row is a record that says "deleted", and another device
        // that still has it needs to hear that.
        write(garageId, VEHICLES, vehicleRepository.getAllForSync().map { it.toSync() }, SyncVehicle.serializer()) { it.id }
        write(garageId, SCHEDULES, scheduleRepository.getAllForSync().map { it.toSync() }, SyncSchedule.serializer()) { it.id }
        write(garageId, LOGS, logRepository.getAllForSync().map { it.toSync() }, SyncLog.serializer()) { it.id }
        write(garageId, ISSUES, issueRepository.getAllForSync().map { it.toSync() }, SyncIssue.serializer()) { it.id }
    }

    private suspend fun <T> write(
        garageId: String,
        collection: String,
        records: List<T>,
        serializer: KSerializer<T>,
        id: (T) -> String,
    ) {
        val ref = db.collection("garages/$garageId/$collection")
        // Firestore caps a batch at 500 writes.
        records.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                batch.set(ref.document(id(record)), json.encodeToJsonElement(serializer, record).toFirestoreMap())
            }
            batch.commit().await()
        }
    }

    private suspend fun localCounts(): Map<String, Int> = mapOf(
        VEHICLES to vehicleRepository.getAllForSync().size,
        SCHEDULES to scheduleRepository.getAllForSync().size,
        LOGS to logRepository.getAllForSync().size,
        ISSUES to issueRepository.getAllForSync().size,
    )

    private suspend fun cloudCounts(garageId: String): Map<String, Int> =
        listOf(VEHICLES, SCHEDULES, LOGS, ISSUES).associateWith { collection ->
            db.collection("garages/$garageId/$collection").count().get(AggregateSource.SERVER).await().count.toInt()
        }

    private fun remember(garageId: String, role: String, email: String) {
        prefs.edit().putString(KEY_GARAGE, garageId).putString(KEY_ROLE, role).putString(KEY_EMAIL, email).apply()
        _membership.value = GarageMembership(garageId, role, email)
    }

    private fun storedMembership() = GarageMembership(
        garageId = prefs.getString(KEY_GARAGE, null),
        role = prefs.getString(KEY_ROLE, null),
        email = prefs.getString(KEY_EMAIL, null),
    )

    private fun uploadedKey(garageId: String) = "uploaded_$garageId"

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_STANDARD = "standard"
        const val VEHICLES = "vehicles"
        const val SCHEDULES = "schedules"
        const val LOGS = "logs"
        const val ISSUES = "issues"
        private const val BATCH_SIZE = 400
        private const val PREFS_NAME = "garage_log_cloud"
        private const val KEY_GARAGE = "garage_id"
        private const val KEY_ROLE = "role"
        private const val KEY_EMAIL = "email"
    }
}

/** kotlinx JSON to the plain maps, lists and primitives Firestore stores. */
internal fun JsonElement.toFirestore(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toFirestore() }
    is JsonArray -> map { it.toFirestore() }
    is JsonPrimitive -> when {
        isString -> content
        else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
}

@Suppress("UNCHECKED_CAST")
private fun JsonElement.toFirestoreMap(): Map<String, Any?> = toFirestore() as Map<String, Any?>
