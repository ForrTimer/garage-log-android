package com.garagelog.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.di.ServiceLocator
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab { Dashboard, Log, Issues, Build, Settings }

data class GarageLogUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val logs: List<LogEntryEntity> = emptyList(),
    val issues: List<IssueEntity> = emptyList(),
    val buildPhases: List<BuildPhaseEntity> = emptyList(),
    val schedules: List<MaintenanceScheduleEntity> = emptyList(),
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
}

private data class RepoBundle(
    val vehicles: List<VehicleEntity>,
    val logs: List<LogEntryEntity>,
    val issues: List<IssueEntity>,
    val buildPhases: List<BuildPhaseEntity>,
    val schedules: List<MaintenanceScheduleEntity>,
)

class GarageLogViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val activeVehicleId = MutableStateFlow<String?>(null)
    private val currentTab = MutableStateFlow(AppTab.Dashboard)
    private val showScheduleScreen = MutableStateFlow(false)
    private val showCostTrendScreen = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    private val repoBundle = combine(
        locator.vehicleRepository.observeAll(),
        locator.logRepository.observeAll(),
        locator.issueRepository.observeAll(),
        locator.buildPhaseRepository.observeAll(),
        locator.scheduleRepository.observeAll(),
    ) { vehicles, logs, issues, phases, schedules ->
        RepoBundle(vehicles, logs, issues, phases, schedules)
    }

    private val uiFlags = combine(activeVehicleId, currentTab, showScheduleScreen, showCostTrendScreen) { a, b, c, d ->
        UiFlags(a, b, c, d)
    }

    private data class UiFlags(
        val activeVehicleId: String?,
        val currentTab: AppTab,
        val showScheduleScreen: Boolean,
        val showCostTrendScreen: Boolean,
    )

    val uiState: StateFlow<GarageLogUiState> = combine(repoBundle, uiFlags) { bundle, flags ->
        GarageLogUiState(
            vehicles = bundle.vehicles,
            logs = bundle.logs,
            issues = bundle.issues,
            buildPhases = bundle.buildPhases,
            schedules = bundle.schedules,
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

    fun saveVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        locator.vehicleRepository.upsert(vehicle)
    }

    fun deleteVehicle(id: String) = viewModelScope.launch {
        locator.logRepository.getAll().filter { it.vehicleId == id }.forEach {
            locator.photoRepository.deleteForOwner(PhotoOwnerType.LOG.name, it.id)
        }
        locator.issueRepository.getAll().filter { it.vehicleId == id }.forEach {
            locator.photoRepository.deleteForOwner(PhotoOwnerType.ISSUE.name, it.id)
        }
        locator.logRepository.deleteForVehicle(id)
        locator.issueRepository.deleteForVehicle(id)
        locator.buildPhaseRepository.deleteForVehicle(id)
        locator.scheduleRepository.deleteForVehicle(id)
        locator.vehicleRepository.delete(id)
        if (activeVehicleId.value == id) activeVehicleId.value = null
    }

    fun saveLog(entry: LogEntryEntity) = viewModelScope.launch {
        locator.logRepository.upsert(entry)
        val mileage = entry.mileage
        if (mileage != null) {
            locator.vehicleRepository.bumpMileageIfHigher(entry.vehicleId, mileage, entry.date)
        }
    }

    fun deleteLog(id: String) = viewModelScope.launch {
        locator.photoRepository.getAll().filter { it.ownerType == PhotoOwnerType.LOG.name && it.ownerId == id }
            .forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.deleteForOwner(PhotoOwnerType.LOG.name, id)
        locator.logRepository.delete(id)
    }

    fun saveIssue(issue: IssueEntity) = viewModelScope.launch { locator.issueRepository.upsert(issue) }

    fun deleteIssue(id: String) = viewModelScope.launch {
        locator.photoRepository.getAll().filter { it.ownerType == PhotoOwnerType.ISSUE.name && it.ownerId == id }
            .forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.deleteForOwner(PhotoOwnerType.ISSUE.name, id)
        locator.issueRepository.delete(id)
    }

    fun saveBuildPhase(phase: BuildPhaseEntity) = viewModelScope.launch { locator.buildPhaseRepository.upsert(phase) }
    fun deleteBuildPhase(id: String) = viewModelScope.launch { locator.buildPhaseRepository.delete(id) }

    fun saveSchedule(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        locator.scheduleRepository.upsert(schedule)
    }
    fun deleteSchedule(id: String) = viewModelScope.launch { locator.scheduleRepository.delete(id) }

    fun markScheduleDoneToday(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        val vehicle = locator.vehicleRepository.getAll().find { it.id == schedule.vehicleId }
        locator.scheduleRepository.upsert(
            schedule.copy(lastDoneMileage = vehicle?.miles ?: schedule.lastDoneMileage, lastDoneDate = todayIso()),
        )
    }

    fun observePhotosForOwner(ownerType: PhotoOwnerType, ownerId: String): Flow<List<PhotoEntity>> =
        locator.photoRepository.observeForOwner(ownerType.name, ownerId)

    fun addPhoto(ownerType: PhotoOwnerType, ownerId: String, sourceUri: Uri) = viewModelScope.launch {
        val path = locator.photoStore.copyIntoAppStorage(sourceUri)
        if (path == null) {
            _messages.emit("Couldn't save that photo.")
            return@launch
        }
        locator.photoRepository.upsert(
            PhotoEntity(
                id = UUID.randomUUID().toString(),
                ownerType = ownerType.name,
                ownerId = ownerId,
                filePath = path,
                addedDate = todayIso(),
            ),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        locator.photoStore.delete(photo.filePath)
        locator.photoRepository.delete(photo)
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
    }

    fun resetToSeed() = viewModelScope.launch {
        locator.backupManager.resetToSeed()
        activeVehicleId.value = null
        _messages.emit("Reset to seed data.")
    }
}

class GarageLogViewModelFactory(private val locator: ServiceLocator) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GarageLogViewModel::class.java))
        return GarageLogViewModel(locator) as T
    }
}
