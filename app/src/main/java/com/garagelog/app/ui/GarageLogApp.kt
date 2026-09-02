package com.garagelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.garagelog.app.ui.theme.GarageFabShape
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.build.BuildScreen
import com.garagelog.app.ui.build.PhaseFormSheet
import com.garagelog.app.ui.build.StepFormSheet
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

private val mainTabs = listOf(AppTab.Dashboard, AppTab.Log, AppTab.Issues, AppTab.Build, AppTab.Settings)

private fun AppTab.label(): String = when (this) {
    AppTab.Dashboard -> "Home"
    AppTab.Log -> "Log"
    AppTab.Issues -> "Issues"
    AppTab.Build -> "Build"
    AppTab.Settings -> "More"
}

private sealed class Sheet {
    data object None : Sheet()
    data class VehicleForm(val vehicle: VehicleEntity?) : Sheet()
    data class LogForm(val entry: LogEntryEntity?) : Sheet()
    data class IssueForm(val issue: IssueEntity?) : Sheet()
    data class PhaseForm(val phase: BuildPhaseEntity?, val vehicleId: String? = null) : Sheet()
    data class StepForm(val step: BuildStepEntity?, val vehicleId: String? = null) : Sheet()
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

    // Always land on Home (and clear the vehicle filter) whenever the app comes to the
    // foreground — cold start or returning from the background — not wherever it was left.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.resetToHome()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState(initialPage = mainTabs.indexOf(uiState.currentTab).coerceAtLeast(0)) { mainTabs.size }
    LaunchedEffect(uiState.currentTab) {
        val target = mainTabs.indexOf(uiState.currentTab)
        if (target >= 0 && pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // settledPage (not currentPage) — currentPage updates continuously mid-swipe/mid-animation,
    // and feeding that back into the ViewModel raced with the animateScrollToPage above,
    // causing the pager to stall fighting itself.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            mainTabs.getOrNull(page)?.let { tab -> if (tab != uiState.currentTab) viewModel.selectTab(tab) }
        }
    }
    val liveTab = mainTabs.getOrNull(pagerState.currentPage) ?: uiState.currentTab

    val showingSubScreen = uiState.showScheduleScreen || uiState.showCostTrendScreen
    val showFab = !showingSubScreen && uiState.currentTab != AppTab.Settings && uiState.currentTab != AppTab.Dashboard

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = garageColors.alarm,
        selectedTextColor = garageColors.alarm,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = Color.Transparent,
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status strip and header share the chrome tone (lighter than content ground) —
                // one flat field, not two competing surfaces.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(garageColors.chrome),
                )
                Surface(color = garageColors.chrome, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        val headerTitle = when {
                            uiState.showScheduleScreen -> "Maintenance"
                            uiState.showCostTrendScreen -> "Cost trend"
                            else -> liveTab.label()
                        }
                        Text(
                            text = "🔧 $headerTitle",
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
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(garageColors.chromeEdge))
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(garageColors.chromeEdge))
                NavigationBar(containerColor = garageColors.chrome) {
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Dashboard && !showingSubScreen,
                        onClick = { viewModel.resetToHome() },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Log && !showingSubScreen,
                        onClick = { viewModel.selectTab(AppTab.Log) },
                        icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                        label = { Text("Log") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Issues && !showingSubScreen,
                        onClick = { viewModel.selectTab(AppTab.Issues) },
                        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                        label = { Text("Issues") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Build && !showingSubScreen,
                        onClick = { viewModel.selectTab(AppTab.Build) },
                        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                        label = { Text("Build") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Settings && !showingSubScreen,
                        onClick = { viewModel.selectTab(AppTab.Settings) },
                        icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                        label = { Text("More") },
                        colors = itemColors,
                    )
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                SmallFloatingActionButton(
                    shape = GarageFabShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        activeSheet = if (uiState.vehicles.isEmpty()) {
                            Sheet.VehicleForm(null)
                        } else {
                            when (uiState.currentTab) {
                                AppTab.Log -> Sheet.LogForm(null)
                                AppTab.Issues -> Sheet.IssueForm(null)
                                AppTab.Build -> Sheet.StepForm(null)
                                else -> Sheet.LogForm(null)
                            }
                        }
                    },
                ) {
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
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (mainTabs[page]) {
                        AppTab.Dashboard -> DashboardScreen(
                            uiState = uiState,
                            onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                            onAddVehicle = { activeSheet = Sheet.VehicleForm(null) },
                            onOpenSchedule = viewModel::openSchedule,
                            onOpenCostTrend = viewModel::openCostTrend,
                            onUpdateMileage = viewModel::updateMileage,
                            onOpenVehicleTab = viewModel::openVehicleTab,
                            onOpenVehicleCostTrend = viewModel::openVehicleCostTrend,
                        )
                        AppTab.Log -> LogScreen(
                            uiState = uiState,
                            onItemClick = { activeSheet = Sheet.LogForm(it) },
                        )
                        AppTab.Issues -> IssuesScreen(
                            uiState = uiState,
                            onItemClick = { activeSheet = Sheet.IssueForm(it) },
                        )
                        AppTab.Build -> BuildScreen(
                            uiState = uiState,
                            onPhaseClick = { activeSheet = Sheet.PhaseForm(it) },
                            onStepClick = { activeSheet = Sheet.StepForm(it) },
                            onAddPhase = { vehicleId -> activeSheet = Sheet.PhaseForm(null, vehicleId) },
                            onImportStepsFromNotes = viewModel::importStepsFromPhaseNotes,
                        )
                        AppTab.Settings -> SettingsScreen(
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
        }
    }

    when (val sheet = activeSheet) {
        is Sheet.None -> Unit
        is Sheet.VehicleForm -> VehicleFormSheet(
            vehicle = sheet.vehicle,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { v, starterServices ->
                viewModel.saveVehicle(v)
                if (starterServices.isNotEmpty()) viewModel.addStarterSchedules(v.id, starterServices)
                activeSheet = Sheet.None
            },
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
            defaultVehicleId = sheet.phase?.vehicleId ?: sheet.vehicleId ?: uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id,
            nextOrder = uiState.buildPhases.size + 1,
            onDismiss = { activeSheet = Sheet.None },
            onSave = { viewModel.saveBuildPhase(it); activeSheet = Sheet.None },
            onDelete = { viewModel.deleteBuildPhase(it); activeSheet = Sheet.None },
        )
        is Sheet.StepForm -> {
            val vehicleId = sheet.step?.vehicleId ?: sheet.vehicleId ?: uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id
            if (vehicleId != null) {
                StepFormSheet(
                    step = sheet.step,
                    vehicleId = vehicleId,
                    phases = uiState.buildPhases.filter { it.vehicleId == vehicleId },
                    nextOrder = uiState.buildSteps.count { it.vehicleId == vehicleId } + 1,
                    viewModel = viewModel,
                    onDismiss = { activeSheet = Sheet.None },
                    onSave = { viewModel.saveBuildStep(it); activeSheet = Sheet.None },
                    onDelete = { viewModel.deleteBuildStep(it); activeSheet = Sheet.None },
                )
            }
        }
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
