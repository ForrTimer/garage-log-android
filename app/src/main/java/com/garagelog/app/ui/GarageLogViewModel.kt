package com.garagelog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.sync.SyncStatus
import com.garagelog.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    data object Members : SubScreen
    data class Diagnosis(val issueId: String) : SubScreen
    /** [issueId] non-null = the follow-up thread on that issue's diagnosis. */
    data class Chat(val vehicleId: String, val issueId: String? = null) : SubScreen

    /** The vehicle filter in the header means nothing on these, so it's hidden for them. */
    val usesVehicleFilter: Boolean get() = this !is Settings && this !is ManageVehicles && this !is Members
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

private data class UiFlags(
    val activeVehicleId: String?,
    val currentTab: AppTab,
    val subScreen: SubScreen?,
)

/**
 * The app shell: where you are (tab, sub-screen, vehicle filter), the records every tab reads,
 * what this person may do in a shared garage, and sync status. Changing records is
 * [RecordsViewModel]'s job; Bob, account settings and the shared garage have their own.
 */
class GarageLogViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val activeVehicleId = MutableStateFlow<String?>(null)
    private val currentTab = MutableStateFlow(AppTab.Dashboard)
    private val subScreen = MutableStateFlow<SubScreen?>(null)

    /** Every ViewModel's confirmations and errors, shown as toasts by GarageLogApp. */
    val messages: SharedFlow<String> = locator.userMessages.messages

    // True once the first real Room read has landed — distinct from uiState's synchronous
    // default value, so the splash screen can wait for actual data instead of dismissing
    // on the next frame regardless of whether anything has loaded yet.
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded

    val syncStatus: StateFlow<SyncStatus> = locator.syncStatusHolder.status

    val access: StateFlow<Access> = locator.garageCloud.membership.map { Access(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Access(locator.garageCloud.membership.value))

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

    private val uiFlags = combine(activeVehicleId, currentTab, subScreen) { a, b, c -> UiFlags(a, b, c) }

    val uiState: StateFlow<GarageLogUiState> = combine(repoBundle, uiFlags) { bundle, flags ->
        GarageLogUiState(
            vehicles = bundle.vehicles,
            logs = bundle.logs,
            issues = bundle.issues,
            schedules = bundle.schedules,
            notificationPrefs = bundle.notificationPrefs,
            // A filter on a vehicle that's gone (deleted here, removed by a sync, a reset to demo
            // data) would show empty tabs; it falls back to "All" instead.
            activeVehicleId = flags.activeVehicleId?.takeIf { id -> bundle.vehicles.any { it.id == id } },
            currentTab = flags.currentTab,
            subScreen = flags.subScreen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GarageLogUiState())

    fun selectVehicle(id: String?) {
        // On a chat the chips pick whose chat it is; "All" has no chat of its own, so it leaves it.
        val open = subScreen.value
        if (open is SubScreen.Chat) {
            if (id != null && id != open.vehicleId) subScreen.value = SubScreen.Chat(id)
            return
        }
        activeVehicleId.value = id
    }

    fun selectTab(tab: AppTab) {
        currentTab.value = tab
        subScreen.value = null
    }

    fun openSchedule() { subScreen.value = SubScreen.Schedule }
    fun openSettings() { subScreen.value = SubScreen.Settings }
    fun openManageVehicles() { subScreen.value = SubScreen.ManageVehicles }
    fun openMembers() { subScreen.value = SubScreen.Members }
    fun openDiagnosis(issueId: String) { subScreen.value = SubScreen.Diagnosis(issueId) }
    fun openChat(vehicleId: String, issueId: String? = null) { subScreen.value = SubScreen.Chat(vehicleId, issueId) }
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

    fun syncNow() = viewModelScope.launch { locator.requestSync() }

    companion object {
        val Factory = viewModelFactory { initializer { GarageLogViewModel(locator()) } }
    }
}
