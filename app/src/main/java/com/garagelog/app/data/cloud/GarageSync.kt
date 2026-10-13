package com.garagelog.app.data.cloud

import android.content.Context
import androidx.room.withTransaction
import com.garagelog.app.data.db.AppDatabase
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.NotificationPrefsRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.data.sync.SyncIssue
import com.garagelog.app.data.sync.SyncLog
import com.garagelog.app.data.sync.SyncNotificationPrefs
import com.garagelog.app.data.sync.SyncSchedule
import com.garagelog.app.data.sync.SyncVehicle
import com.garagelog.app.data.sync.Syncable
import com.garagelog.app.data.sync.toEntity
import com.garagelog.app.data.sync.toSync
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Two-way sync between Room and the shared garage in Firestore. Replaces the Drive snapshot for
 * anyone in a garage; the merge rule is the one Drive used — per record, the newer `updatedAt`
 * wins, tombstones included — applied per document instead of per snapshot file.
 *
 * Each run pulls, then pushes:
 * - **Pull** what changed since this device last looked, by `serverUpdatedAt`: a server timestamp
 *   set on every write. Pulling by the server's clock rather than `updatedAt` (each device's own
 *   clock) means a device whose clock runs behind can't write something another device's cursor
 *   has already passed.
 * - **Push** rows edited here since the last push (`updatedAt` above a per-collection cursor),
 *   skipping any that just arrived in the pull, so a pulled change isn't echoed straight back.
 *
 * A Standard member pulls only their vehicles' records and pushes vehicles and schedules as the
 * field-masked updates firestore.rules allows (odometer; "last done"). Their pulls can't use the
 * timestamp cursor: `vehicleId in [...]` plus a range on another field needs a composite index,
 * and at family scale fetching their few vehicles' records whole is cheaper than managing one.
 *
 * Known gap: two people editing the same record within one sync's window — between this device's
 * pull and its push — is resolved by whoever pushes last, not by `updatedAt`. Rare at this scale.
 */
class GarageSync(
    context: Context,
    private val database: AppDatabase,
    private val cloud: GarageCloud,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val notificationPrefsRepository: NotificationPrefsRepository,
    private val onNotificationPrefsPulled: (NotificationPrefsEntity) -> Unit,
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Per collection, this run's pulled documents that have no server stamp yet. */
    private val unstamped = mutableMapOf<String, Set<String>>()

    /** True when this device is in a garage and signed in to Firebase, i.e. sync() has work to do. */
    val isActive: Boolean
        get() = auth.currentUser != null && cloud.membership.value.garageId != null

    suspend fun sync() {
        val uid = auth.currentUser?.uid ?: return
        val garageId = cloud.membership.value.garageId ?: return

        // Re-read membership every run: an admin may have changed this person's role or vehicles.
        val member = db.document("garages/$garageId/members/$uid").get(Source.SERVER).await()
        val role = member.getString("role") ?: error("No longer a member of this garage.")
        val vehicleIds = (member.get("vehicleIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        cloud.updateRole(role)
        val scope = if (role == GarageCloud.ROLE_ADMIN) Scope.All else Scope.Vehicles(vehicleIds)

        // Pull everything first, apply it in one transaction (one Room invalidation, one UI
        // recompute — the lesson from the Drive sync's row-at-a-time freeze), then push.
        val pulled = Pulled(
            vehicles = pull(garageId, GarageCloud.VEHICLES, scope, SyncVehicle.serializer()),
            schedules = pull(garageId, GarageCloud.SCHEDULES, scope, SyncSchedule.serializer()),
            logs = pull(garageId, GarageCloud.LOGS, scope, SyncLog.serializer()),
            issues = pull(garageId, GarageCloud.ISSUES, scope, SyncIssue.serializer()),
        )
        apply(pulled)

        push(garageId, GarageCloud.VEHICLES, vehicleRepository.getAllForSync().map { it.toSync() }, pulled.vehicles, scope, SyncVehicle.serializer(), VEHICLE_MASK) { it.id }
        push(garageId, GarageCloud.SCHEDULES, scheduleRepository.getAllForSync().map { it.toSync() }, pulled.schedules, scope, SyncSchedule.serializer(), SCHEDULE_MASK) { it.vehicleId }
        push(garageId, GarageCloud.LOGS, logRepository.getAllForSync().map { it.toSync() }, pulled.logs, scope, SyncLog.serializer(), null) { it.vehicleId }
        push(garageId, GarageCloud.ISSUES, issueRepository.getAllForSync().map { it.toSync() }, pulled.issues, scope, SyncIssue.serializer(), null) { it.vehicleId }

        syncNotificationPrefs(uid)
    }

    private sealed interface Scope {
        data object All : Scope
        data class Vehicles(val ids: List<String>) : Scope
    }

    private data class Pulled(
        val vehicles: List<SyncVehicle>,
        val schedules: List<SyncSchedule>,
        val logs: List<SyncLog>,
        val issues: List<SyncIssue>,
    )

    private suspend fun <T> pull(garageId: String, collection: String, scope: Scope, serializer: KSerializer<T>): List<T> {
        val ref = db.collection("garages/$garageId/$collection")
        val docs: List<DocumentSnapshot> = when (scope) {
            Scope.All -> {
                val cursor = pullCursor(garageId, collection)
                val query = if (cursor == null) ref else ref.whereGreaterThan(SERVER_UPDATED_AT, cursor)
                val result = query.get(Source.SERVER).await().documents
                // Documents written before sync existed (the first upload) carry no server stamp, so
                // no cursor can ever pass them; the push below re-sends them once to stamp them.
                unstamped[collection] = result.filter { it.getTimestamp(SERVER_UPDATED_AT) == null }.map { it.id }.toSet()
                android.util.Log.d("GarageSync", "pull $collection since $cursor: ${result.size} docs, ${unstamped[collection]?.size} unstamped")
                result.mapNotNull { it.getTimestamp(SERVER_UPDATED_AT) }.maxOrNull()?.let { setPullCursor(garageId, collection, it) }
                result
            }
            // Firestore caps an `in` at 30 values.
            is Scope.Vehicles -> scope.ids.chunked(30).flatMap { chunk ->
                val field = if (collection == GarageCloud.VEHICLES) FieldPath.documentId() else FieldPath.of("vehicleId")
                ref.whereIn(field, chunk).get(Source.SERVER).await().documents
            }
        }
        return docs.mapNotNull { doc -> doc.data?.let { decode(it, serializer) } }
    }

    private suspend fun apply(pulled: Pulled) {
        val localVehicles = vehicleRepository.getAllForSync().associateBy { it.id }
        val localSchedules = scheduleRepository.getAllForSync().associateBy { it.id }
        val localLogs = logRepository.getAllForSync().associateBy { it.id }
        val localIssues = issueRepository.getAllForSync().associateBy { it.id }
        database.withTransaction {
            // photoPath isn't synced (display photos stay on the device, Ethan's call), and
            // toEntity() would null it — the bug that ate vehicle photos under Drive sync.
            pulled.vehicles.newerThan(localVehicles.mapValues { it.value.updatedAt }).forEach {
                vehicleRepository.upsert(it.toEntity().copy(photoPath = localVehicles[it.id]?.photoPath))
            }
            pulled.schedules.newerThan(localSchedules.mapValues { it.value.updatedAt }).forEach { scheduleRepository.upsert(it.toEntity()) }
            pulled.logs.newerThan(localLogs.mapValues { it.value.updatedAt }).forEach { logRepository.upsert(it.toEntity()) }
            pulled.issues.newerThan(localIssues.mapValues { it.value.updatedAt }).forEach { issueRepository.upsert(it.toEntity()) }
        }
    }

    private fun <T : Syncable> List<T>.newerThan(local: Map<String, Long>): List<T> =
        filter { remote -> local[remote.id]?.let { remote.updatedAt > it } ?: true }

    /**
     * [vehicleOf] gives the vehicle a record belongs to (a vehicle's own id for vehicles), for
     * scoping a Standard member's pushes. [mask] is the fields a Standard member may write on this
     * collection, or null if they may write whole records.
     */
    private suspend fun <T : Syncable> push(
        garageId: String,
        collection: String,
        local: List<T>,
        justPulled: List<T>,
        scope: Scope,
        serializer: KSerializer<T>,
        mask: List<String>?,
        vehicleOf: (T) -> String,
    ) {
        val cursorKey = "push_${garageId}_$collection"
        val cursor = prefs.getLong(cursorKey, 0L)
        val arrived = justPulled.associate { it.id to it.updatedAt }
        val changed = local.filter { it.updatedAt > cursor }
        // Only an admin may write whole records, so only an admin's device restamps.
        val restamp = if (scope is Scope.All) unstamped[collection].orEmpty() else emptySet()
        if (changed.isEmpty() && restamp.isEmpty()) return

        val toSend = (changed.filter { arrived[it.id] != it.updatedAt } + local.filter { it.id in restamp })
            .distinctBy { it.id }
            .filter { scope !is Scope.Vehicles || vehicleOf(it) in scope.ids }
        val ref = db.collection("garages/$garageId/$collection")
        toSend.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val fields = json.encodeToJsonElement(serializer, record).toFirestore().asMap()
                if (scope is Scope.Vehicles && mask != null) {
                    batch.update(ref.document(record.id), fields.filterKeys { it in mask } + (SERVER_UPDATED_AT to FieldValue.serverTimestamp()))
                } else {
                    batch.set(ref.document(record.id), fields + (SERVER_UPDATED_AT to FieldValue.serverTimestamp()))
                }
            }
            batch.commit().await()
        }
        // Everything considered this run is now either in the garage or came from it.
        changed.maxOfOrNull { it.updatedAt }?.let { prefs.edit().putLong(cursorKey, it).apply() }
    }

    /** Reminder settings are per person, not per garage: users/{uid}/private/notificationPrefs. */
    private suspend fun syncNotificationPrefs(uid: String) {
        val ref = db.document("users/$uid/private/notificationPrefs")
        val local = notificationPrefsRepository.get().toSync()
        val remote = ref.get(Source.SERVER).await().data?.let { decode(it, SyncNotificationPrefs.serializer()) }
        when {
            remote != null && remote.updatedAt > local.updatedAt -> {
                notificationPrefsRepository.upsert(remote.toEntity())
                onNotificationPrefsPulled(remote.toEntity())
            }
            remote == null || local.updatedAt > remote.updatedAt ->
                ref.set(json.encodeToJsonElement(SyncNotificationPrefs.serializer(), local).toFirestore().asMap(), SetOptions.merge()).await()
        }
    }

    private fun <T> decode(data: Map<String, Any?>, serializer: KSerializer<T>): T? =
        runCatching { json.decodeFromJsonElement(serializer, data.toJson()) }.getOrNull()

    private fun pullCursor(garageId: String, collection: String): Timestamp? {
        val seconds = prefs.getLong("pull_${garageId}_${collection}_s", -1L)
        if (seconds < 0) return null
        return Timestamp(seconds, prefs.getInt("pull_${garageId}_${collection}_n", 0))
    }

    private fun setPullCursor(garageId: String, collection: String, at: Timestamp) {
        prefs.edit()
            .putLong("pull_${garageId}_${collection}_s", at.seconds)
            .putInt("pull_${garageId}_${collection}_n", at.nanoseconds)
            .apply()
    }

    companion object {
        const val SERVER_UPDATED_AT = "serverUpdatedAt"
        private const val PREFS_NAME = "garage_log_cloud_sync"
        private const val BATCH_SIZE = 400
        // Must match what firestore.rules lets a Standard member change.
        private val VEHICLE_MASK = listOf("miles", "milesDate", "updatedAt")
        private val SCHEDULE_MASK = listOf("lastDoneMileage", "lastDoneDate", "updatedAt")
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

/** Firestore's maps back to kotlinx JSON. Timestamps (serverUpdatedAt) aren't model fields; dropped. */
private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(
        entries.filter { it.value !is Timestamp }.associate { it.key.toString() to it.value.toJson() },
    )
    is List<*> -> JsonArray(map { it.toJson() })
    else -> JsonNull
}
