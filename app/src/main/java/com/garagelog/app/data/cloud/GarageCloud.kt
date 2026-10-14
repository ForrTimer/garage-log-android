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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * What admins let Standard members do, per garage (garages/{g}.standardPermissions). Defaults are
 * the rules' defaults: a missing key means these values, so a garage that never touched the
 * switches behaves exactly like one that saved them as shown.
 */
data class StandardPermissions(
    val deleteEntries: Boolean = false,
    val editOthersEntries: Boolean = true,
    val editSchedules: Boolean = false,
    val editVehicles: Boolean = false,
) {
    fun toMap(): Map<String, Boolean> = mapOf(
        "deleteEntries" to deleteEntries,
        "editOthersEntries" to editOthersEntries,
        "editSchedules" to editSchedules,
        "editVehicles" to editVehicles,
    )

    companion object {
        fun from(map: Map<*, *>?): StandardPermissions {
            val d = StandardPermissions()
            fun flag(key: String, default: Boolean) = map?.get(key) as? Boolean ?: default
            return StandardPermissions(
                deleteEntries = flag("deleteEntries", d.deleteEntries),
                editOthersEntries = flag("editOthersEntries", d.editOthersEntries),
                editSchedules = flag("editSchedules", d.editSchedules),
                editVehicles = flag("editVehicles", d.editVehicles),
            )
        }
    }
}

/** Which shared garage this device works in, and as what. Null garageId = not set up yet. */
data class GarageMembership(
    val garageId: String?,
    val role: String?,
    val email: String?,
    val uid: String? = null,
    /** Standard members only: the vehicles they were given. Admins see every vehicle. */
    val vehicleIds: List<String> = emptyList(),
    val permissions: StandardPermissions = StandardPermissions(),
) {
    val isStandard: Boolean get() = garageId != null && role == GarageCloud.ROLE_STANDARD
    val isAdmin: Boolean get() = garageId != null && role == GarageCloud.ROLE_ADMIN
}

/** An invitation waiting for [email] (invites/{email}). */
data class GarageInvite(
    val email: String,
    val garageId: String,
    val garageName: String,
    val role: String,
    val vehicleIds: List<String>,
    val invitedByName: String,
)

/** A member as an admin sees them on the Members screen. */
data class GarageMember(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: String,
    val vehicleIds: List<String>,
    val isOwner: Boolean,
)

/** Everything the Members screen shows, read in one go. */
data class MembersPage(
    val garageName: String,
    val members: List<GarageMember>,
    val invites: List<GarageInvite>,
    val permissions: StandardPermissions,
)

/** What signing in found: a garage to work in, or an invitation to answer first. */
sealed interface GarageSetupOutcome {
    data class Ready(val result: GarageSetupResult) : GarageSetupOutcome
    data class Invited(val invite: GarageInvite, val localVehicleCount: Int) : GarageSetupOutcome
}

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
 * Setting up either creates the owner's garage and uploads the device's records into it (one
 * document per row, keyed by the same ids Room uses and serialized through the same Sync* models
 * the Drive sync used), or finds an invitation and lets the person join someone else's. Admins
 * manage members, invites and Standard permissions from here too; GarageSync does the syncing.
 */
class GarageCloud(
    context: Context,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val localData: LocalGarageData,
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true }

    private val _membership = MutableStateFlow(storedMembership())
    val membership: StateFlow<GarageMembership> = _membership.asStateFlow()

    /** The signed-in Firebase account, or "" — stamped as createdBy on new logs and issues. */
    val currentUid: String get() = auth.currentUser?.uid.orEmpty()

    /**
     * Finds this account's garage, or an invitation to someone else's (returned for the person to
     * answer — nothing is joined or created yet), or else creates a garage with the owner as its
     * admin and uploads this device's records. Safe to run again: every write is keyed by the
     * record's own id, so a re-run after a failure overwrites rather than duplicates.
     */
    suspend fun setUpGarage(): GarageSetupOutcome {
        val user = requireNotNull(auth.currentUser) { "Not signed in to Firebase." }
        val uid = user.uid
        val email = user.email.orEmpty().lowercase()

        val existing = (db.document("users/$uid").get().await().get("garageIds") as? List<*>)
            ?.filterIsInstance<String>()?.firstOrNull()
        if (existing == null) {
            findInvite(email)?.let { invite ->
                return GarageSetupOutcome.Invited(invite, vehicleRepository.getAll().size)
            }
        }
        val created = existing == null
        val garageId = existing ?: createGarage(uid, email, user.displayName)

        val member = db.document("garages/$garageId/members/$uid").get().await()
        val role = member.getString("role") ?: error("This account isn't a member of its garage.")
        val garage = db.document("garages/$garageId").get().await()
        remember(
            GarageMembership(
                garageId = garageId,
                role = role,
                email = email,
                uid = uid,
                vehicleIds = member.stringList("vehicleIds"),
                permissions = StandardPermissions.from(garage.get("standardPermissions") as? Map<*, *>),
            ),
        )

        // Only an admin's device seeds the garage, and only once: after that, sync owns the data.
        val shouldUpload = role == ROLE_ADMIN && !prefs.getBoolean(uploadedKey(garageId), false)
        val local = localCounts()
        if (shouldUpload) {
            upload(garageId)
            prefs.edit().putBoolean(uploadedKey(garageId), true).apply()
        }
        return GarageSetupOutcome.Ready(
            GarageSetupResult(
                garageId = garageId,
                created = created,
                uploaded = shouldUpload,
                local = local,
                cloud = if (shouldUpload) cloudCounts(garageId) else emptyMap(),
            ),
        )
    }

    private suspend fun findInvite(email: String): GarageInvite? {
        if (email.isBlank()) return null
        return db.document("invites/$email").get().await().takeIf { it.exists() }?.toInvite()
    }

    /**
     * Joins [invite]'s garage. An admin's records come along: this phone's push cursor for a garage
     * it has never synced starts at zero, so the first sync uploads everything here, merged by id.
     * A Standard member's don't (they can't add vehicles to someone else's garage), so with
     * [clearLocal] the phone's own records are removed first and it shows only what it was given.
     */
    suspend fun acceptInvite(invite: GarageInvite, clearLocal: Boolean) {
        val user = requireNotNull(auth.currentUser) { "Not signed in to Firebase." }
        val email = user.email.orEmpty().lowercase()
        val now = System.currentTimeMillis()
        // The rules check the member doc against the invite, so the invite goes only after.
        db.document("garages/${invite.garageId}/members/${user.uid}").set(
            mapOf(
                "role" to invite.role,
                "vehicleIds" to invite.vehicleIds,
                "email" to email,
                "displayName" to user.displayName.orEmpty(),
                "updatedAt" to now,
            ),
        ).await()
        db.document("users/${user.uid}").set(
            mapOf("email" to email, "displayName" to user.displayName.orEmpty(), "garageIds" to FieldValue.arrayUnion(invite.garageId)),
            SetOptions.merge(),
        ).await()
        db.document("invites/${invite.email}").delete().await()

        if (clearLocal) localData.clearAll()
        // Nothing to seed: sync takes it from here, in both directions.
        prefs.edit().putBoolean(uploadedKey(invite.garageId), true).apply()
        val garage = db.document("garages/${invite.garageId}").get().await()
        remember(
            GarageMembership(
                garageId = invite.garageId,
                role = invite.role,
                email = email,
                uid = user.uid,
                vehicleIds = invite.vehicleIds,
                permissions = StandardPermissions.from(garage.get("standardPermissions") as? Map<*, *>),
            ),
        )
    }

    // ---- Admin: members, invites, permissions ----

    suspend fun loadMembersPage(): MembersPage {
        val garageId = requireNotNull(_membership.value.garageId) { "Not in a shared garage." }
        val garage = db.document("garages/$garageId").get().await()
        val owner = garage.getString("createdBy").orEmpty()
        val members = db.collection("garages/$garageId/members").get().await().documents.map { doc ->
            GarageMember(
                uid = doc.id,
                email = doc.getString("email").orEmpty(),
                displayName = doc.getString("displayName").orEmpty(),
                role = doc.getString("role") ?: ROLE_STANDARD,
                vehicleIds = doc.stringList("vehicleIds"),
                isOwner = doc.id == owner,
            )
        }.sortedWith(compareByDescending<GarageMember> { it.isOwner }.thenBy { it.displayName.ifBlank { it.email } })
        val invites = db.collection("invites").whereEqualTo("garageId", garageId).get().await()
            .documents.mapNotNull { it.toInvite() }.sortedBy { it.email }
        return MembersPage(
            garageName = garage.getString("name").orEmpty(),
            members = members,
            invites = invites,
            permissions = StandardPermissions.from(garage.get("standardPermissions") as? Map<*, *>),
        )
    }

    /** Creates or replaces the invitation for [email]. They join the next time they sign in. */
    suspend fun invite(email: String, role: String, vehicleIds: List<String>) {
        val garageId = requireNotNull(_membership.value.garageId) { "Not in a shared garage." }
        val user = requireNotNull(auth.currentUser) { "Not signed in to Firebase." }
        val key = email.trim().lowercase()
        require(key.contains('@')) { "That doesn't look like an email address." }
        val garageName = db.document("garages/$garageId").get().await().getString("name").orEmpty()
        db.document("invites/$key").set(
            mapOf(
                "email" to key,
                "garageId" to garageId,
                "garageName" to garageName,
                "role" to role,
                // Admins see every vehicle; their list is kept empty so it can't mislead later.
                "vehicleIds" to if (role == ROLE_ADMIN) emptyList() else vehicleIds,
                "invitedBy" to user.uid,
                "invitedByName" to user.displayName.orEmpty(),
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    suspend fun withdrawInvite(email: String) {
        db.document("invites/$email").delete().await()
    }

    suspend fun updateMember(uid: String, role: String, vehicleIds: List<String>) {
        val garageId = requireNotNull(_membership.value.garageId) { "Not in a shared garage." }
        db.document("garages/$garageId/members/$uid").update(
            mapOf(
                "role" to role,
                "vehicleIds" to if (role == ROLE_ADMIN) emptyList() else vehicleIds,
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    /** Their phone notices on its next sync and drops the garage. */
    suspend fun removeMember(uid: String) {
        val garageId = requireNotNull(_membership.value.garageId) { "Not in a shared garage." }
        db.document("garages/$garageId/members/$uid").delete().await()
    }

    suspend fun savePermissions(permissions: StandardPermissions) {
        val garageId = requireNotNull(_membership.value.garageId) { "Not in a shared garage." }
        db.document("garages/$garageId").update(
            mapOf("standardPermissions" to permissions.toMap(), "updatedAt" to System.currentTimeMillis()),
        ).await()
        _membership.value = _membership.value.copy(permissions = permissions).also { save(it) }
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
                batch.set(
                    ref.document(id(record)),
                    json.encodeToJsonElement(serializer, record).toFirestoreMap() +
                        (GarageSync.SERVER_UPDATED_AT to FieldValue.serverTimestamp()),
                )
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

    /**
     * Sync re-reads the member doc and the garage each run: an admin may have changed this
     * person's role, their vehicles, or what Standard members may do.
     */
    fun updateAccess(role: String, vehicleIds: List<String>, permissions: StandardPermissions) {
        val current = _membership.value
        if (current.garageId == null) return
        val next = current.copy(role = role, vehicleIds = vehicleIds, permissions = permissions, uid = current.uid ?: auth.currentUser?.uid)
        if (next != current) remember(next)
    }

    /** An admin removed this person: the phone stops syncing with the garage. */
    fun forgetGarage() {
        prefs.edit().remove(KEY_GARAGE).remove(KEY_ROLE).remove(KEY_VEHICLES).remove(KEY_PERMISSIONS).apply()
        _membership.value = GarageMembership(null, null, _membership.value.email)
    }

    private fun remember(membership: GarageMembership) {
        save(membership)
        _membership.value = membership
    }

    private fun save(membership: GarageMembership) {
        prefs.edit()
            .putString(KEY_GARAGE, membership.garageId)
            .putString(KEY_ROLE, membership.role)
            .putString(KEY_EMAIL, membership.email)
            .putString(KEY_UID, membership.uid)
            .putString(KEY_VEHICLES, membership.vehicleIds.joinToString(","))
            .putString(KEY_PERMISSIONS, json.encodeToString(MapSerializer(String.serializer(), Boolean.serializer()), membership.permissions.toMap()))
            .apply()
    }

    private fun storedMembership(): GarageMembership {
        val permissions = prefs.getString(KEY_PERMISSIONS, null)?.let { stored ->
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), Boolean.serializer()), stored) }.getOrNull()
        }
        return GarageMembership(
            garageId = prefs.getString(KEY_GARAGE, null),
            role = prefs.getString(KEY_ROLE, null),
            email = prefs.getString(KEY_EMAIL, null),
            uid = prefs.getString(KEY_UID, null) ?: auth.currentUser?.uid,
            vehicleIds = prefs.getString(KEY_VEHICLES, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            permissions = StandardPermissions.from(permissions),
        )
    }

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
        private const val KEY_UID = "uid"
        private const val KEY_VEHICLES = "vehicle_ids"
        private const val KEY_PERMISSIONS = "standard_permissions"
    }
}

internal fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

private fun DocumentSnapshot.toInvite(): GarageInvite? {
    val email = getString("email") ?: id
    val garageId = getString("garageId") ?: return null
    return GarageInvite(
        email = email,
        garageId = garageId,
        garageName = getString("garageName").orEmpty(),
        role = getString("role") ?: GarageCloud.ROLE_STANDARD,
        vehicleIds = stringList("vehicleIds"),
        invitedByName = getString("invitedByName").orEmpty(),
    )
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
