package com.garagelog.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.StepPriority
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.sync.SyncStatus
import com.garagelog.app.data.auth.SignInStep
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.notifications.MileageReminderScheduler
import com.garagelog.app.util.CommonMaintenanceServices
import com.garagelog.app.util.assignBuildBuckets
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
import kotlinx.coroutines.launch

enum class AppTab { Dashboard, Log, Issues, Build, Settings }

data class GarageLogUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val logs: List<LogEntryEntity> = emptyList(),
    val issues: List<IssueEntity> = emptyList(),
    val buildPhases: List<BuildPhaseEntity> = emptyList(),
    val buildSteps: List<BuildStepEntity> = emptyList(),
    val schedules: List<MaintenanceScheduleEntity> = emptyList(),
    val notificationPrefs: NotificationPrefsEntity = NotificationPrefsEntity(),
    val activeVehicleId: String? = null,
    val currentTab: AppTab = AppTab.Dashboard,
    val showScheduleScreen: Boolean = false,
    val showCostTrendScreen: Boolean = false,
) {
    val activeVehicle: VehicleEntity? get() = vehicles.find { it.id == activeVehicleId }
    fun vehicleName(id: String): String = vehicles.find { it.id == id }?.name ?: "Unknown"
    fun logsFor(vehicleId: String?): List<LogEntryEntity> = vehicleId?.let { id -> logs.filter { it.vehicleId == id } } ?: logs
    fun issuesFor(vehicleId: String?): List<IssueEntity> = vehicleId?.let { id -> issues.filter { it.vehicleId == id } } ?: issues
    fun schedulesFor(vehicleId: String?): List<MaintenanceScheduleEntity> =
        vehicleId?.let { id -> schedules.filter { it.vehicleId == id } } ?: schedules
    fun stepsFor(vehicleId: String?): List<BuildStepEntity> = vehicleId?.let { id -> buildSteps.filter { it.vehicleId == id } } ?: buildSteps
}

private data class RepoBundle(
    val vehicles: List<VehicleEntity>,
    val logs: List<LogEntryEntity>,
    val issues: List<IssueEntity>,
    val buildPhases: List<BuildPhaseEntity>,
    val schedules: List<MaintenanceScheduleEntity>,
)

private data class ExtraBundle(
    val buildSteps: List<BuildStepEntity>,
    val notificationPrefs: NotificationPrefsEntity,
)

class GarageLogViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val activeVehicleId = MutableStateFlow<String?>(null)
    private val currentTab = MutableStateFlow(AppTab.Dashboard)
    private val showScheduleScreen = MutableStateFlow(false)
    private val showCostTrendScreen = MutableStateFlow(false)

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
        locator.buildPhaseRepository.observeAll(),
        locator.scheduleRepository.observeAll(),
    ) { vehicles, logs, issues, phases, schedules ->
        RepoBundle(vehicles, logs, issues, phases, schedules)
    }

    init {
        viewModelScope.launch {
            repoBundle.first()
            _isDataLoaded.value = true
        }
    }

    private val extraBundle = combine(
        locator.buildStepRepository.observeAll(),
        locator.notificationPrefsRepository.observe(),
    ) { steps, prefs -> ExtraBundle(steps, prefs) }

    private val uiFlags = combine(activeVehicleId, currentTab, showScheduleScreen, showCostTrendScreen) { a, b, c, d ->
        UiFlags(a, b, c, d)
    }

    private data class UiFlags(
        val activeVehicleId: String?,
        val currentTab: AppTab,
        val showScheduleScreen: Boolean,
        val showCostTrendScreen: Boolean,
    )

    val uiState: StateFlow<GarageLogUiState> = combine(repoBundle, extraBundle, uiFlags) { bundle, extra, flags ->
        GarageLogUiState(
            vehicles = bundle.vehicles,
            logs = bundle.logs,
            issues = bundle.issues,
            buildPhases = bundle.buildPhases,
            buildSteps = extra.buildSteps,
            schedules = bundle.schedules,
            notificationPrefs = extra.notificationPrefs,
            activeVehicleId = flags.activeVehicleId,
            currentTab = flags.currentTab,
            showScheduleScreen = flags.showScheduleScreen,
            showCostTrendScreen = flags.showCostTrendScreen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GarageLogUiState())

    fun selectVehicle(id: String?) { activeVehicleId.value = id }
    fun selectTab(tab: AppTab) {
        currentTab.value = tab
        showScheduleScreen.value = false
        showCostTrendScreen.value = false
    }
    fun openSchedule() { showScheduleScreen.value = true }
    fun openCostTrend() { showCostTrendScreen.value = true }
    fun closeSubScreen() { showScheduleScreen.value = false; showCostTrendScreen.value = false }

    /** Filters to [vehicleId] and switches to [tab] — e.g. tapping a dashboard stat. */
    fun openVehicleTab(vehicleId: String, tab: AppTab) {
        activeVehicleId.value = vehicleId
        selectTab(tab)
    }

    fun openVehicleCostTrend(vehicleId: String) {
        activeVehicleId.value = vehicleId
        showScheduleScreen.value = false
        showCostTrendScreen.value = true
    }

    /** Resets to the Home tab with no vehicle filter — cold start, app resume, and tapping Home. */
    fun resetToHome() {
        activeVehicleId.value = null
        selectTab(AppTab.Dashboard)
    }

    private fun requestSync() = locator.requestSync()

    fun saveVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        locator.vehicleRepository.upsert(vehicle.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteVehicle(id: String) = viewModelScope.launch {
        locator.vehicleRepository.getAll().find { it.id == id }?.photoPath?.let { locator.photoStore.delete(it) }
        locator.photoRepository.softDeleteForVehicle(id)
        locator.logRepository.softDeleteForVehicle(id)
        locator.issueRepository.softDeleteForVehicle(id)
        locator.buildPhaseRepository.softDeleteForVehicle(id)
        locator.buildStepRepository.softDeleteForVehicle(id)
        locator.scheduleRepository.softDeleteForVehicle(id)
        locator.vehicleRepository.softDelete(id)
        if (activeVehicleId.value == id) activeVehicleId.value = null
        requestSync()
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

    fun updateMileage(vehicle: VehicleEntity, mileage: Int) = viewModelScope.launch {
        locator.vehicleRepository.bumpMileageIfHigher(vehicle.id, mileage, todayIso())
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
        requestSync()
    }

    fun saveBuildPhase(phase: BuildPhaseEntity) = viewModelScope.launch {
        locator.buildPhaseRepository.upsert(phase.copy(updatedAt = System.currentTimeMillis()))
        rebucketForVehicle(phase.vehicleId)
        requestSync()
    }

    fun deleteBuildPhase(id: String) = viewModelScope.launch {
        val phase = locator.buildPhaseRepository.getAll().find { it.id == id }
        locator.buildPhaseRepository.softDelete(id)
        phase?.let { rebucketForVehicle(it.vehicleId) }
        requestSync()
    }

    fun saveBuildStep(step: BuildStepEntity) = viewModelScope.launch {
        locator.buildStepRepository.upsert(step.copy(updatedAt = System.currentTimeMillis()))
        rebucketForVehicle(step.vehicleId)
        requestSync()
    }

    fun deleteBuildStep(id: String) = viewModelScope.launch {
        val step = locator.buildStepRepository.getAll().find { it.id == id }
        locator.photoRepository.getForOwner(PhotoOwnerType.BUILD_STEP.name, id).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(PhotoOwnerType.BUILD_STEP.name, id)
        locator.buildStepRepository.softDelete(id)
        step?.let { rebucketForVehicle(it.vehicleId) }
        requestSync()
    }

    /**
     * Re-runs [assignBuildBuckets] for one vehicle's steps/phases and persists any change.
     * A step moving from one non-null phase to a different non-null phase is a genuine
     * promotion (budget freed up, priority reshuffled) worth telling the owner about;
     * first-time assignment (null -> phase) and unbucketing (phase -> null) stay silent.
     */
    private suspend fun rebucketForVehicle(vehicleId: String) {
        val steps = locator.buildStepRepository.getAll().filter { it.vehicleId == vehicleId }
        val phases = locator.buildPhaseRepository.getAll().filter { it.vehicleId == vehicleId }
        val assignments = assignBuildBuckets(steps, phases)
        val now = System.currentTimeMillis()
        assignments.forEach { (stepId, newPhaseId) ->
            val step = steps.find { it.id == stepId } ?: return@forEach
            if (step.phaseId == newPhaseId) return@forEach
            locator.buildStepRepository.upsert(step.copy(phaseId = newPhaseId, updatedAt = now))
            if (step.phaseId != null && newPhaseId != null) {
                val phaseName = phases.find { it.id == newPhaseId }?.phase ?: "another phase"
                _messages.emit("\"${step.title}\" moved to $phaseName.")
            }
        }
    }

    /**
     * One-time bootstrap: splits a phase's free-text notes on commas into individual
     * [BuildStepEntity] checklist items, so there's real starting data instead of a blank
     * checklist. Pinned to this phase (manualPhaseOverride) so the auto-bucketer leaves them
     * put. No-ops if the phase already has steps, so it's safe to invoke more than once.
     */
    fun importStepsFromPhaseNotes(phase: BuildPhaseEntity) = viewModelScope.launch {
        val existingSteps = locator.buildStepRepository.getAll().filter { it.phaseId == phase.id }
        if (existingSteps.isNotEmpty()) return@launch
        // Split on commas that separate list items, not thousands-separator commas inside a
        // number like "$1,250" — a comma immediately followed by a digit doesn't count.
        val titles = phase.notes.split(Regex(",(?!\\d)")).map { it.trim().trim('.') }.filter { it.isNotBlank() }
        if (titles.isEmpty()) return@launch
        val now = System.currentTimeMillis()
        titles.forEachIndexed { index, title ->
            locator.buildStepRepository.upsert(
                BuildStepEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = phase.vehicleId,
                    phaseId = phase.id,
                    title = title,
                    notes = "",
                    priority = StepPriority.Medium.name,
                    status = PhaseStatus.NotStarted.label,
                    estimatedCost = null,
                    actualCost = null,
                    order = index + 1,
                    manualPhaseOverride = true,
                    updatedAt = now,
                ),
            )
        }
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

    fun syncNow() = requestSync()

    fun saveNotificationPrefs(context: Context, prefs: NotificationPrefsEntity) = viewModelScope.launch {
        locator.notificationPrefsRepository.upsert(prefs)
        MileageReminderScheduler.reschedule(context.applicationContext, prefs)
    }
}

class GarageLogViewModelFactory(private val locator: ServiceLocator) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GarageLogViewModel::class.java))
        return GarageLogViewModel(locator) as T
    }
}
