package com.garagelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.R
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.build.BuildScreen
import com.garagelog.app.ui.build.PhaseFormSheet
import com.garagelog.app.ui.components.VehiclePickerRow
import com.garagelog.app.ui.costtrend.CostTrendScreen
import com.garagelog.app.ui.dashboard.DashboardScreen
import com.garagelog.app.ui.issues.IssueFormSheet
import com.garagelog.app.ui.issues.IssuesScreen
import com.garagelog.app.ui.log.LogFormSheet
import com.garagelog.app.ui.log.LogScreen
import com.garagelog.app.ui.schedule.ScheduleFormSheet
import com.garagelog.app.ui.schedule.ScheduleScreen
import com.garagelog.app.ui.settings.SettingsScreen
import com.garagelog.app.ui.settings.VehicleFormSheet
import kotlinx.coroutines.flow.collectLatest

private sealed class Sheet {
    data object None : Sheet()
    data class VehicleForm(val vehicle: VehicleEntity?) : Sheet()
    data class LogForm(val entry: LogEntryEntity?) : Sheet()
    data class IssueForm(val issue: IssueEntity?) : Sheet()
    data class PhaseForm(val phase: BuildPhaseEntity?) : Sheet()
    data class ScheduleForm(val schedule: MaintenanceScheduleEntity?) : Sheet()
}

@Composable
fun GarageLogApp(viewModel: GarageLogViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var activeSheet by remember { mutableStateOf<Sheet>(Sheet.None) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val showingSubScreen = uiState.showScheduleScreen || uiState.showCostTrendScreen
    val showFab = !showingSubScreen && uiState.currentTab != AppTab.Settings

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Solid black status-bar strip — deliberately not the header's surface color,
                // so the system clock/icons never sit on top of a color seam.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(Color.Black),
                )
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "🔧 " + stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp),
                        )
                        VehiclePickerRow(
                            vehicles = uiState.vehicles,
                            activeVehicleId = uiState.activeVehicleId,
                            onSelect = viewModel::selectVehicle,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.Dashboard && !showingSubScreen,
                    onClick = { viewModel.selectTab(AppTab.Dashboard) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.Log && !showingSubScreen,
                    onClick = { viewModel.selectTab(AppTab.Log) },
                    icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                    label = { Text("Log") },
                )
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.Issues && !showingSubScreen,
                    onClick = { viewModel.selectTab(AppTab.Issues) },
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                    label = { Text("Issues") },
                )
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.Build && !showingSubScreen,
                    onClick = { viewModel.selectTab(AppTab.Build) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("Build") },
                )
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.Settings && !showingSubScreen,
                    onClick = { viewModel.selectTab(AppTab.Settings) },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                    label = { Text("More") },
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = {
                    activeSheet = if (uiState.vehicles.isEmpty()) {
                        Sheet.VehicleForm(null)
                    } else {
                        when (uiState.currentTab) {
                            AppTab.Log -> Sheet.LogForm(null)
                            AppTab.Issues -> Sheet.IssueForm(null)
                            AppTab.Build -> Sheet.PhaseForm(null)
                            else -> Sheet.LogForm(null)
                        }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            when {
                uiState.showScheduleScreen -> ScheduleScreen(
                    uiState = uiState,
                    onBack = viewModel::closeSubScreen,
                    onEdit = { activeSheet = Sheet.ScheduleForm(it) },
                    onAddNew = { activeSheet = Sheet.ScheduleForm(null) },
                    onMarkDone = viewModel::markScheduleDoneToday,
                )
                uiState.showCostTrendScreen -> CostTrendScreen(
                    uiState = uiState,
                    onBack = viewModel::closeSubScreen,
                )
                uiState.currentTab == AppTab.Dashboard -> DashboardScreen(
                    uiState = uiState,
                    onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                    onOpenSchedule = viewModel::openSchedule,
                    onOpenCostTrend = viewModel::openCostTrend,
                )
                uiState.currentTab == AppTab.Log -> LogScreen(
                    uiState = uiState,
                    onItemClick = { activeSheet = Sheet.LogForm(it) },
                )
                uiState.currentTab == AppTab.Issues -> IssuesScreen(
                    uiState = uiState,
                    onItemClick = { activeSheet = Sheet.IssueForm(it) },
                )
                uiState.currentTab == AppTab.Build -> BuildScreen(
                    uiState = uiState,
                    onItemClick = { activeSheet = Sheet.PhaseForm(it) },
                )
                uiState.currentTab == AppTab.Settings -> SettingsScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onAddVehicle = { activeSheet = Sheet.VehicleForm(null) },
                    onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                    onOpenSchedule = viewModel::openSchedule,
                    onOpenCostTrend = viewModel::openCostTrend,
                )
            }
        }
    }

    when (val sheet = activeSheet) {
        is Sheet.None -> Unit
        is Sheet.VehicleForm -> VehicleFormSheet(
            vehicle = sheet.vehicle,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveVehicle(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteVehicle(it); activeSheet = Sheet.None },
        )
        is Sheet.LogForm -> LogFormSheet(
            entry = sheet.entry,
            vehicles = uiState.vehicles,
            defaultVehicleId = uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id,
            viewModel = viewModel,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveLog(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteLog(it); activeSheet = Sheet.None },
        )
        is Sheet.IssueForm -> IssueFormSheet(
            issue = sheet.issue,
            vehicles = uiState.vehicles,
            defaultVehicleId = uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id,
            viewModel = viewModel,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveIssue(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteIssue(it); activeSheet = Sheet.None },
        )
        is Sheet.PhaseForm -> PhaseFormSheet(
            phase = sheet.phase,
            vehicles = uiState.vehicles,
            defaultVehicleId = uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id,
            nextOrder = uiState.buildPhases.size + 1,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveBuildPhase(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteBuildPhase(it); activeSheet = Sheet.None },
        )
        is Sheet.ScheduleForm -> ScheduleFormSheet(
            schedule = sheet.schedule,
            vehicles = uiState.vehicles,
            defaultVehicleId = uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveSchedule(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteSchedule(it); activeSheet = Sheet.None },
        )
    }
}
