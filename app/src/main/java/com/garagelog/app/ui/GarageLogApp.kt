package com.garagelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.build.BuildScreen
import com.garagelog.app.ui.build.PhaseFormSheet
import com.garagelog.app.ui.build.StepFormSheet
import com.garagelog.app.ui.components.VehiclePickerRow
import com.garagelog.app.ui.costtrend.CostTrendScreen
import com.garagelog.app.ui.dashboard.DashboardScreen
import com.garagelog.app.ui.dashboard.VehicleReorderScreen
import com.garagelog.app.ui.issues.IssueFormSheet
import com.garagelog.app.ui.issues.IssuesScreen
import com.garagelog.app.ui.log.LogFormSheet
import com.garagelog.app.ui.log.LogScreen
import com.garagelog.app.ui.schedule.ScheduleFormSheet
import com.garagelog.app.ui.schedule.ScheduleScreen
import com.garagelog.app.ui.settings.SettingsScreen
import com.garagelog.app.ui.settings.VehicleFormSheet
import com.garagelog.app.util.todayIso
import kotlinx.coroutines.flow.collectLatest

private val mainTabs = listOf(AppTab.Dashboard, AppTab.Log, AppTab.Issues, AppTab.Build, AppTab.Settings)

// A quick round-trip through a system picker (photo, backup file) stops and restarts this
// activity in well under this long; a genuine "put the phone down" gap runs much longer.
private const val HOME_RESET_AWAY_THRESHOLD_MS = 30_000L

private fun AppTab.label(): String = when (this) {
    AppTab.Dashboard -> "Home"
    AppTab.Log -> "Log"
    AppTab.Issues -> "Issues"
    AppTab.Build -> "Build"
    AppTab.Settings -> "More"
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.Dashboard -> Icons.Filled.Home
    AppTab.Log -> Icons.Filled.MenuBook
    AppTab.Issues -> Icons.Filled.Warning
    AppTab.Build -> Icons.Filled.Build
    AppTab.Settings -> Icons.Filled.MoreHoriz
}

private sealed class Sheet {
    data object None : Sheet()
    data class VehicleForm(val vehicle: VehicleEntity?) : Sheet()
    data class LogForm(val entry: LogEntryEntity?, val prefill: LogEntryEntity? = null) : Sheet()
    data class IssueForm(val issue: IssueEntity?) : Sheet()
    data class PhaseForm(val phase: BuildPhaseEntity?, val vehicleId: String? = null) : Sheet()
    data class StepForm(val step: BuildStepEntity?, val vehicleId: String? = null) : Sheet()
    data class ScheduleForm(val schedule: MaintenanceScheduleEntity?) : Sheet()
}

@Composable
fun GarageLogApp(viewModel: GarageLogViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var activeSheet by remember { mutableStateOf<Sheet>(Sheet.None) }
    var reorderMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Navigating away (tapping another bottom-nav tab) bails out of reorder mode rather than
    // leaving it stuck active on a tab that has nothing to do with vehicle order — any drag not
    // yet confirmed via "Done" is simply discarded, same as dismissing any other in-progress edit.
    LaunchedEffect(uiState.currentTab) {
        if (uiState.currentTab != AppTab.Dashboard) reorderMode = false
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Always land on Home (and clear the vehicle filter) whenever the app comes to the
    // foreground after genuinely being put down for a while — cold start, or picked back up
    // later — but NOT wherever it was left for every ON_START, since picking a photo (or
    // exporting/importing a backup file) launches a separate system activity that stops and
    // restarts this one for the few seconds it's on screen. Resetting unconditionally on every
    // ON_START meant finishing an ordinary in-app action that happened to involve a system
    // picker silently dumped you back on Home instead of wherever you were working. Only reset
    // if this activity was actually stopped for longer than a real "put the phone down" gap.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stoppedAtMillis: Long? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAtMillis = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val stoppedAt = stoppedAtMillis
                    if (stoppedAt == null || System.currentTimeMillis() - stoppedAt > HOME_RESET_AWAY_THRESHOLD_MS) {
                        viewModel.resetToHome()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState(initialPage = mainTabs.indexOf(uiState.currentTab).coerceAtLeast(0)) { mainTabs.size }
    // Reorder mode replaces the pager entirely (same pattern as the schedule/cost-trend
    // sub-screens below) rather than being one of its pages — but unlike those sub-screens,
    // entering it does change uiState.currentTab (via resetToHome(), so "Done" lands back on
    // Home). That's what makes this pair of effects dangerous here: pagerState.animateScrollToPage
    // needs a mounted HorizontalPager to actually scroll against, and calling it while the pager
    // isn't composed (because we're showing VehicleReorderScreen instead) left it in a corrupted
    // state — settledPage would land on the wrong page once observed, which fed back into
    // viewModel.selectTab with the WRONG tab, silently cancelling reorder mode via the "navigated
    // away" effect above. Guarding both effects while reorderMode is true avoids ever touching
    // pagerState while it has nothing real to scroll.
    LaunchedEffect(uiState.currentTab) {
        if (reorderMode) return@LaunchedEffect
        val target = mainTabs.indexOf(uiState.currentTab)
        if (target >= 0 && pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // settledPage (not currentPage) — currentPage updates continuously mid-swipe/mid-animation,
    // and feeding that back into the ViewModel raced with the animateScrollToPage above,
    // causing the pager to stall fighting itself.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            if (reorderMode) return@collectLatest
            mainTabs.getOrNull(page)?.let { tab -> if (tab != uiState.currentTab) viewModel.selectTab(tab) }
        }
    }
    // The guard above means the pager can silently drift out of sync with currentTab for the
    // whole time reorder mode is active (it was on "Settings" when we left it, currentTab is now
    // "Dashboard") — the two effects above won't naturally re-fire to fix that on their own once
    // reorder mode ends, since currentTab itself doesn't change again on exit. Explicitly resync
    // (instantly, not animated — there's nothing on screen for an animation to be visible against
    // until this frame anyway) the moment reorder mode closes.
    LaunchedEffect(reorderMode) {
        if (reorderMode) return@LaunchedEffect
        val target = mainTabs.indexOf(uiState.currentTab)
        if (target >= 0) pagerState.scrollToPage(target)
    }
    val liveTab = mainTabs.getOrNull(pagerState.currentPage) ?: uiState.currentTab

    val showingSubScreen = uiState.showScheduleScreen || uiState.showCostTrendScreen
    val showFab = !showingSubScreen && uiState.currentTab != AppTab.Settings && uiState.currentTab != AppTab.Dashboard

    // Deliberately not garageColors.alarm here — that red is reserved for genuine urgency
    // (overdue maintenance, safety-critical issues). Reusing it for "this is the selected tab"
    // would make every screen look like something's wrong just because you're looking at it.
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = garageColors.info,
        selectedTextColor = garageColors.info,
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
                        // liveTab is derived from pagerState.currentPage, which reorder mode
                        // deliberately leaves un-resynced until it closes (see the pager-guard
                        // comment above) — so it can't be trusted for the header while reorder
                        // mode is active, same as it isn't trusted for the schedule/cost-trend
                        // sub-screens below.
                        val headerTitle = when {
                            reorderMode -> "Home"
                            uiState.showScheduleScreen -> "Maintenance"
                            uiState.showCostTrendScreen -> "Cost trend"
                            else -> liveTab.label()
                        }
                        val headerIcon = if (reorderMode) Icons.Filled.Home else liveTab.icon()
                        // A large, low-opacity watermark of the current tab's icon behind the
                        // title — sized off the header's own width so it scales with the phone,
                        // and clipped to the fixed-height box so it crops top/bottom instead of
                        // pushing the header taller.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(64.dp).clipToBounds()) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                modifier = Modifier.size(maxWidth * 0.9f).align(Alignment.Center),
                            )
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                            )
                        }
                        // The filter row doesn't do anything useful here — VehicleReorderScreen
                        // always shows every vehicle regardless of this filter — so hiding it
                        // avoids implying a selection that reorder mode would just ignore.
                        if (!reorderMode) {
                            VehiclePickerRow(
                                vehicles = uiState.vehicles,
                                activeVehicleId = uiState.activeVehicleId,
                                onSelect = viewModel::selectVehicle,
                            )
                        }
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
                reorderMode -> VehicleReorderScreen(
                    vehicles = uiState.vehicles,
                    onReorderVehicles = viewModel::reorderVehicles,
                    onDone = { reorderMode = false },
                )
                uiState.showScheduleScreen -> ScheduleScreen(
                    uiState = uiState,
                    onBack = viewModel::closeSubScreen,
                    onEdit = { activeSheet = Sheet.ScheduleForm(it) },
                    onAddNew = { activeSheet = Sheet.ScheduleForm(null) },
                    onMarkDone = viewModel::markScheduleDoneToday,
                    onCopySchedule = viewModel::copySchedulesToVehicle,
                    onLogSchedule = { sched ->
                        val vehicle = uiState.vehicles.find { it.id == sched.vehicleId }
                        activeSheet = Sheet.LogForm(
                            entry = null,
                            prefill = LogEntryEntity(
                                id = "",
                                vehicleId = sched.vehicleId,
                                date = todayIso(),
                                mileage = vehicle?.miles,
                                category = LogCategory.Routine.name,
                                task = sched.taskName,
                                cost = null,
                                parts = "",
                                notes = "",
                                fulfillsScheduleId = sched.id,
                            ),
                        )
                    },
                )
                uiState.showCostTrendScreen -> CostTrendScreen(
                    uiState = uiState,
                    onBack = viewModel::closeSubScreen,
                )
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (mainTabs[page]) {
                        AppTab.Dashboard -> DashboardScreen(
                            uiState = uiState,
                            syncStatus = syncStatus,
                            onRefresh = viewModel::syncNow,
                            onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                            onAddVehicle = { activeSheet = Sheet.VehicleForm(null) },
                            onOpenSchedule = viewModel::openSchedule,
                            onOpenCostTrend = viewModel::openCostTrend,
                            onUpdateMileage = viewModel::updateMileage,
                            onOpenVehicleTab = viewModel::openVehicleTab,
                            onOpenVehicleCostTrend = viewModel::openVehicleCostTrend,
                            onSetVehiclePhoto = viewModel::setVehiclePhoto,
                        )
                        AppTab.Log -> LogScreen(
                            uiState = uiState,
                            onItemClick = { activeSheet = Sheet.LogForm(it) },
                            onDelete = { viewModel.deleteLog(it.id) },
                        )
                        AppTab.Issues -> IssuesScreen(
                            uiState = uiState,
                            onItemClick = { activeSheet = Sheet.IssueForm(it) },
                            onDelete = { viewModel.deleteIssue(it.id) },
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
                            onReorderVehicles = {
                                viewModel.resetToHome()
                                reorderMode = true
                            },
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
            viewModel = viewModel,
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
            prefill = sheet.prefill,
            vehicles = uiState.vehicles,
            schedules = uiState.schedules,
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
            onResolvedWithLog = { resolved ->
                viewModel.saveIssue(resolved)
                activeSheet = Sheet.LogForm(
                    entry = null,
                    prefill = LogEntryEntity(
                        id = "",
                        vehicleId = resolved.vehicleId,
                        date = todayIso(),
                        mileage = uiState.vehicles.find { it.id == resolved.vehicleId }?.miles,
                        category = LogCategory.Repair.name,
                        task = resolved.title,
                        cost = null,
                        parts = "",
                        notes = "",
                    ),
                )
            },
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
