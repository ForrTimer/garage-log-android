package com.garagelog.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.garagelog.app.data.ai.AiRunState
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.sync.SyncStatus
import com.garagelog.app.data.auth.SignInStep
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.notifications.MileageReminderScheduler
import com.garagelog.app.util.CommonMaintenanceServices
import com.garagelog.app.util.todayIso
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab { Dashboard, Log, Issues, Trends }

/**
 * Full-screen destinations that sit on top of the tab pager rather than being tabs themselves —
 * reached from the drawer or from a row on a tab. One sealed type rather than a flag per screen:
 * they're mutually exclusive by nature, and a bool each drifted out of sync as they multiplied.
 */
sealed interface SubScreen {
    data object Schedule : SubScreen
    data object Settings : SubScreen
    data object ManageVehicles : SubScreen
    data class Diagnosis(val issueId: String) : SubScreen
    /** [issueId] non-null = the follow-up thread on that issue's diagnosis. */
    data class Chat(val vehicleId: String, val issueId: String? = null) : SubScreen

    /** The vehicle filter in the header means nothing on these, so it's hidden for them. */
    val usesVehicleFilter: Boolean get() = this !is Settings && this !is ManageVehicles
}

data class GarageLogUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val logs: List<LogEntryEntity> = emptyList(),
    val issues: List<IssueEntity> = emptyList(),
    val schedules: List<MaintenanceScheduleEntity> = emptyList(),
    val notificationPrefs: NotificationPrefsEntity = NotificationPrefsEntity(),
    val activeVehicleId: String? = null,
    val currentTab: AppTab = AppTab.Dashboard,
    val subScreen: SubScreen? = null,
) {
    val activeVehicle: VehicleEntity? get() = vehicles.find { it.id == activeVehicleId }
    fun vehicleName(id: String): String = vehicles.find { it.id == id }?.name ?: "Unknown"
    fun logsFor(vehicleId: String?): List<LogEntryEntity> = vehicleId?.let { id -> logs.filter { it.vehicleId == id } } ?: logs
    fun issuesFor(vehicleId: String?): List<IssueEntity> = vehicleId?.let { id -> issues.filter { it.vehicleId == id } } ?: issues
    fun schedulesFor(vehicleId: String?): List<MaintenanceScheduleEntity> =
        vehicleId?.let { id -> schedules.filter { it.vehicleId == id } } ?: schedules
}

private data class RepoBundle(
    val vehicles: List<VehicleEntity>,
    val logs: List<LogEntryEntity>,
    val issues: List<IssueEntity>,
    val schedules: List<MaintenanceScheduleEntity>,
    val notificationPrefs: NotificationPrefsEntity,
)

class GarageLogViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val activeVehicleId = MutableStateFlow<String?>(null)
    private val currentTab = MutableStateFlow(AppTab.Dashboard)
    private val subScreen = MutableStateFlow<SubScreen?>(null)

    /**
     * Keyed by what the request is for, and owned by a process-wide holder rather than this
     * ViewModel, so an answer in flight survives the screen being closed or the app being left.
     */
    val aiRuns: StateFlow<Map<String, AiRunState>> = locator.aiRunHolder.runs

    val hasAiKey: StateFlow<Boolean> = locator.aiKeyStore.hasKey

    val diagnoses: StateFlow<Map<String, AiDiagnosisEntity>> =
        locator.aiRepository.observeDiagnoses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val chatMessages: StateFlow<List<AiChatMessageEntity>> =
        locator.aiRepository.observeChat()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    private val _pendingDriveConsent = MutableSharedFlow<IntentSender>()
    val pendingDriveConsent: SharedFlow<IntentSender> = _pendingDriveConsent

    // True once the first real Room read has landed — distinct from uiState's synchronous
    // default value, so the splash screen can wait for actual data instead of dismissing
    // on the next frame regardless of whether anything has loaded yet.
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded

    val signedInEmail: StateFlow<String?> = locator.authManager.signedInEmail
    val syncStatus: StateFlow<SyncStatus> = locator.syncStatusHolder.status

    private val repoBundle = combine(
        locator.vehicleRepository.observeAll(),
        locator.logRepository.observeAll(),
        locator.issueRepository.observeAll(),
        locator.scheduleRepository.observeAll(),
        locator.notificationPrefsRepository.observe(),
    ) { vehicles, logs, issues, schedules, prefs ->
        RepoBundle(vehicles, logs, issues, schedules, prefs)
    }

    init {
        viewModelScope.launch {
            repoBundle.first()
            _isDataLoaded.value = true
        }
    }

    private val uiFlags = combine(activeVehicleId, currentTab, subScreen) { a, b, c ->
        UiFlags(a, b, c)
    }

    private data class UiFlags(
        val activeVehicleId: String?,
        val currentTab: AppTab,
        val subScreen: SubScreen?,
    )

    val uiState: StateFlow<GarageLogUiState> = combine(repoBundle, uiFlags) { bundle, flags ->
        GarageLogUiState(
            vehicles = bundle.vehicles,
            logs = bundle.logs,
            issues = bundle.issues,
            schedules = bundle.schedules,
            notificationPrefs = bundle.notificationPrefs,
            activeVehicleId = flags.activeVehicleId,
            currentTab = flags.currentTab,
            subScreen = flags.subScreen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GarageLogUiState())

    fun selectVehicle(id: String?) { activeVehicleId.value = id }
    fun selectTab(tab: AppTab) {
        currentTab.value = tab
        subScreen.value = null
    }
    fun openSchedule() { subScreen.value = SubScreen.Schedule }
    fun openSettings() { subScreen.value = SubScreen.Settings }
    fun openManageVehicles() { subScreen.value = SubScreen.ManageVehicles }
    fun closeSubScreen() { subScreen.value = null }

    /** Filters to [vehicleId] and switches to [tab] — e.g. tapping a dashboard stat. */
    fun openVehicleTab(vehicleId: String, tab: AppTab) {
        activeVehicleId.value = vehicleId
        selectTab(tab)
    }

    /** Resets to the Home tab with no vehicle filter — cold start, app resume, and tapping Home. */
    fun resetToHome() {
        activeVehicleId.value = null
        selectTab(AppTab.Dashboard)
    }

    private suspend fun requestSync() = locator.requestSync()

    fun saveVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        locator.vehicleRepository.upsert(vehicle.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteVehicle(id: String) = viewModelScope.launch {
        locator.vehicleRepository.getAll().find { it.id == id }?.photoPath?.let { locator.photoStore.delete(it) }
        locator.photoRepository.softDeleteForVehicle(id)
        locator.logRepository.softDeleteForVehicle(id)
        locator.issueRepository.softDeleteForVehicle(id)
        locator.scheduleRepository.softDeleteForVehicle(id)
        locator.vehicleRepository.softDelete(id)
        // AI data is hard-deleted, not soft-deleted — it never syncs, so there's no peer that
        // needs to learn the row is gone.
        locator.aiRepository.deleteForVehicle(id)
        if (activeVehicleId.value == id) activeVehicleId.value = null
        requestSync()
    }

    /**
     * Copies a freshly-picked photo into app storage right away and hands the resulting stable
     * file path back via [onComplete] — used by VehicleFormSheet so the sheet can preview/carry
     * the photo through to Save without holding onto the picker's own content:// Uri. That Uri's
     * read grant is short-lived (Android's Photo Picker doesn't intend it to be held onto), so a
     * photo held only as a Uri until Save was tapped would go unreadable within seconds.
     * Runs in viewModelScope rather than a Composable-scoped coroutine so the copy itself can't
     * be cancelled by anything happening to the sheet's composition while the picker is on top —
     * only the (harmless-if-dropped) UI callback is at risk of firing into a disposed sheet.
     */
    fun copyPhotoToAppStorage(sourceUri: Uri, onComplete: (String?) -> Unit) = viewModelScope.launch {
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, UUID.randomUUID().toString())
        onComplete(path)
    }

    /** Deletes a photo file directly — for a staged-but-not-yet-saved copy from [copyPhotoToAppStorage]. */
    fun deletePhotoFile(path: String) {
        locator.photoStore.delete(path)
    }

    fun setVehiclePhoto(vehicle: VehicleEntity, sourceUri: Uri) = viewModelScope.launch {
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, UUID.randomUUID().toString()) ?: return@launch
        vehicle.photoPath?.let { locator.photoStore.delete(it) }
        locator.vehicleRepository.upsert(vehicle.copy(photoPath = path, updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun removeVehiclePhoto(vehicle: VehicleEntity) = viewModelScope.launch {
        vehicle.photoPath?.let { locator.photoStore.delete(it) }
        locator.vehicleRepository.upsert(vehicle.copy(photoPath = null, updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    /**
     * The Dashboard's quick "Update mileage" dialog — deliberately lighter-weight than opening a
     * full Log entry sheet for something this routine, but every mileage change still needs to be
     * tracked for the Trends tab's odometer chart, so it writes a Mileage-category log entry
     * alongside the vehicle bump rather than updating the vehicle silently.
     */
    fun updateMileage(vehicle: VehicleEntity, mileage: Int) = viewModelScope.launch {
        val today = todayIso()
        locator.vehicleRepository.bumpMileageIfHigher(vehicle.id, mileage, today)
        locator.logRepository.upsert(
            LogEntryEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                date = today,
                mileage = mileage,
                category = LogCategory.Mileage.name,
                task = "Mileage update",
                cost = null,
                parts = "",
                notes = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    /** Persists a new home-tab display order after the user drags a vehicle tile to a new spot. */
    fun reorderVehicles(orderedIds: List<String>) = viewModelScope.launch {
        val vehicles = locator.vehicleRepository.getAll()
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            val vehicle = vehicles.find { it.id == id } ?: return@forEachIndexed
            if (vehicle.sortOrder != index) locator.vehicleRepository.upsert(vehicle.copy(sortOrder = index, updatedAt = now))
        }
        requestSync()
    }

    /** Copies every active maintenance schedule item from one vehicle onto another, as fresh (not-yet-done) entries. */
    fun copySchedulesToVehicle(sourceVehicleId: String, targetVehicleId: String) = viewModelScope.launch {
        val schedules = locator.scheduleRepository.getAll().filter { it.vehicleId == sourceVehicleId }
        val now = System.currentTimeMillis()
        schedules.forEach { sched ->
            locator.scheduleRepository.upsert(
                sched.copy(
                    id = UUID.randomUUID().toString(),
                    vehicleId = targetVehicleId,
                    lastDoneMileage = null,
                    lastDoneDate = null,
                    updatedAt = now,
                ),
            )
        }
        requestSync()
        _messages.emit(if (schedules.size == 1) "Copied 1 maintenance item." else "Copied ${schedules.size} maintenance items.")
    }

    /** Adds a batch of starter maintenance schedules (see VehicleFormSheet's common-services checklist). */
    fun addStarterSchedules(vehicleId: String, taskNames: List<String>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        taskNames.forEach { name ->
            val template = CommonMaintenanceServices.byName[name] ?: return@forEach
            locator.scheduleRepository.upsert(
                MaintenanceScheduleEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    taskName = name,
                    intervalMiles = template.intervalMiles,
                    intervalMonths = template.intervalMonths,
                    lastDoneMileage = null,
                    lastDoneDate = null,
                    updatedAt = now,
                ),
            )
        }
        requestSync()
    }

    fun saveLog(entry: LogEntryEntity) = viewModelScope.launch {
        locator.logRepository.upsert(entry.copy(updatedAt = System.currentTimeMillis()))
        val mileage = entry.mileage
        if (mileage != null) {
            locator.vehicleRepository.bumpMileageIfHigher(entry.vehicleId, mileage, entry.date)
        }
        // A Routine entry linked to a schedule item (via the "Fulfills schedule" picker) is the
        // maintenance actually happening — use the log's own date/mileage (which may be backfilled
        // for a past service, not necessarily "today"/"current miles") rather than duplicating the
        // separate "mark done today" quick-action's today-and-current-mileage assumption.
        //
        // Only ever ADVANCE the schedule's lastDoneDate, never regress it. Without this guard,
        // retroactively linking several existing entries to the same schedule (editing/saving
        // them one at a time) let whichever one happened to be saved *last* silently overwrite a
        // more recent entry's already-correct due date — e.g. linking an April oil-change entry
        // after a September one was already linked reverted the schedule to "overdue since April"
        // even though the real last oil change was in September. ISO yyyy-MM-dd strings compare
        // correctly with plain string comparison. The one trade-off: correcting the *date itself*
        // on the entry that currently defines the schedule's due date (a typo fix, say) won't take
        // effect this way — use the "Last done" fields on the schedule directly (Maintenance →
        // edit) for that rarer case.
        val scheduleId = entry.fulfillsScheduleId
        if (entry.category == LogCategory.Routine.name && scheduleId != null) {
            locator.scheduleRepository.getAll().find { it.id == scheduleId }?.let { schedule ->
                val currentLastDone = schedule.lastDoneDate
                if (currentLastDone == null || entry.date >= currentLastDone) {
                    locator.scheduleRepository.upsert(
                        schedule.copy(
                            lastDoneMileage = entry.mileage ?: schedule.lastDoneMileage,
                            lastDoneDate = entry.date,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        requestSync()
    }

    fun deleteLog(id: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(PhotoOwnerType.LOG.name, id).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(PhotoOwnerType.LOG.name, id)
        locator.logRepository.softDelete(id)
        requestSync()
    }

    fun saveIssue(issue: IssueEntity) = viewModelScope.launch {
        locator.issueRepository.upsert(issue.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteIssue(id: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(PhotoOwnerType.ISSUE.name, id).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(PhotoOwnerType.ISSUE.name, id)
        locator.issueRepository.softDelete(id)
        locator.aiRepository.deleteDiagnosisForIssue(id)
        requestSync()
    }

    fun saveSchedule(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        locator.scheduleRepository.upsert(schedule.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteSchedule(id: String) = viewModelScope.launch {
        locator.scheduleRepository.softDelete(id)
        requestSync()
    }

    fun markScheduleDoneToday(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        val vehicle = locator.vehicleRepository.getAll().find { it.id == schedule.vehicleId }
        locator.scheduleRepository.upsert(
            schedule.copy(
                lastDoneMileage = vehicle?.miles ?: schedule.lastDoneMileage,
                lastDoneDate = todayIso(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    fun observePhotosForOwner(ownerType: PhotoOwnerType, ownerId: String): Flow<List<PhotoEntity>> =
        locator.photoRepository.observeForOwner(ownerType.name, ownerId)

    fun addPhoto(ownerType: PhotoOwnerType, ownerId: String, sourceUri: Uri) = viewModelScope.launch {
        val photoId = UUID.randomUUID().toString()
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, photoId)
        if (path == null) {
            _messages.emit("Couldn't save that photo.")
            return@launch
        }
        locator.photoRepository.upsert(
            PhotoEntity(
                id = photoId,
                ownerType = ownerType.name,
                ownerId = ownerId,
                filePath = path,
                addedDate = todayIso(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        locator.photoStore.delete(photo.filePath)
        locator.photoRepository.softDelete(photo.id)
        requestSync()
    }

    /**
     * Cleans up photos attached to an owner that's about to be discarded without ever being
     * saved — e.g. a new log entry the user picked photos onto (photos need a real id to attach
     * to, so a not-yet-saved entry's id is provisional) and then backed out of instead of saving.
     * Same soft-delete + file-delete pattern as an existing entry's own delete path.
     */
    fun discardPhotosForOwner(ownerType: PhotoOwnerType, ownerId: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(ownerType.name, ownerId).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(ownerType.name, ownerId)
        requestSync()
    }

    fun openDiagnosis(issueId: String) { subScreen.value = SubScreen.Diagnosis(issueId) }

    fun openChat(vehicleId: String, issueId: String? = null) {
        subScreen.value = SubScreen.Chat(vehicleId, issueId)
    }

    /** Both of these hand off to [com.garagelog.app.data.ai.AiWorker] rather than running here,
     * so the request outlives this ViewModel and the answer is saved even if the app is closed. */
    fun runDiagnosis(issue: IssueEntity) {
        locator.aiRunHolder.clearError(issue.id)
        locator.enqueueDiagnosis(issue.id, issue.title)
    }

    fun sendChatMessage(vehicleId: String, issueId: String?, question: String, label: String) {
        if (question.isBlank()) return
        locator.aiRunHolder.clearError(AiChatMessageEntity.threadKey(vehicleId, issueId))
        locator.enqueueChat(vehicleId, issueId, question.trim(), label)
    }

    fun cancelDiagnosis(issueId: String) {
        locator.cancelAiWork("ai-diagnosis-$issueId")
        locator.aiRunHolder.finish(issueId)
    }

    fun cancelChat(vehicleId: String, issueId: String?) {
        locator.cancelAiWork("ai-chat-${issueId ?: vehicleId}")
        locator.aiRunHolder.finish(AiChatMessageEntity.threadKey(vehicleId, issueId))
    }

    fun clearChat(vehicleId: String, issueId: String?) = viewModelScope.launch {
        locator.aiRepository.clearThread(vehicleId, issueId)
    }

    fun setAiApiKey(key: String) {
        locator.aiKeyStore.setApiKey(key)
        viewModelScope.launch { _messages.emit("API key saved.") }
    }

    fun clearAiApiKey() {
        locator.aiKeyStore.clear()
        viewModelScope.launch { _messages.emit("API key removed.") }
    }

    fun aiKeyHint(): String? = locator.aiKeyStore.keyHint()

    fun decodeAiSources(sourcesJson: String) = locator.aiRepository.decodeSources(sourcesJson)

    fun exportBackup(output: OutputStream) = viewModelScope.launch {
        runCatching { locator.backupManager.exportToStream(output) }
            .onSuccess { _messages.emit("Backup exported.") }
            .onFailure { _messages.emit("Export failed: ${it.message}") }
    }

    fun importBackup(input: InputStream) = viewModelScope.launch {
        val result = locator.backupManager.importFromStream(input)
        _messages.emit(
            if (result.isSuccess) "Backup imported." else "Could not read that file: ${result.exceptionOrNull()?.message}",
        )
        if (result.isSuccess) requestSync()
    }

    fun resetToSeed() = viewModelScope.launch {
        locator.backupManager.resetToSeed()
        locator.aiRepository.deleteAll()
        activeVehicleId.value = null
        _messages.emit("Reset to seed data.")
        requestSync()
    }

    fun beginSignIn(activity: Activity) = viewModelScope.launch {
        val result = locator.authManager.beginSignIn(activity)
        result.onSuccess { step ->
            when (step) {
                is SignInStep.Complete -> {
                    _messages.emit("Signed in as ${step.email}.")
                    requestSync()
                }
                is SignInStep.NeedsDriveConsent -> _pendingDriveConsent.emit(step.intentSender)
            }
        }.onFailure { _messages.emit("Sign-in failed: ${it.message}") }
    }

    fun completeDriveConsent(data: Intent?) = viewModelScope.launch {
        val result = locator.authManager.completeDriveConsent(data)
        result.onSuccess { step ->
            _messages.emit("Signed in as ${step.email}.")
            requestSync()
        }.onFailure { _messages.emit("Couldn't finish connecting Drive: ${it.message}") }
    }

    fun signOut() = viewModelScope.launch {
        locator.authManager.signOut()
        _messages.emit("Signed out.")
    }

    fun syncNow() = viewModelScope.launch { requestSync() }

    fun saveNotificationPrefs(context: Context, prefs: NotificationPrefsEntity) = viewModelScope.launch {
        val stamped = prefs.copy(updatedAt = System.currentTimeMillis())
        locator.notificationPrefsRepository.upsert(stamped)
        MileageReminderScheduler.reschedule(context.applicationContext, stamped)
        requestSync()
    }
}

class GarageLogViewModelFactory(private val locator: ServiceLocator) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GarageLogViewModel::class.java))
        return GarageLogViewModel(locator) as T
    }
}
