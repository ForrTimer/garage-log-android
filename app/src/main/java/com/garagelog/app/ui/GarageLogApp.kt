package com.garagelog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.ui.ai.AiChatScreen
import com.garagelog.app.ui.ai.AiDiagnosisScreen
import com.garagelog.app.ui.ai.BobViewModel
import com.garagelog.app.ui.components.GarageDrawer
import com.garagelog.app.ui.components.VehiclePickerRow
import com.garagelog.app.ui.dashboard.DashboardScreen
import com.garagelog.app.ui.dashboard.VehicleReorderScreen
import com.garagelog.app.ui.issues.IssuesScreen
import com.garagelog.app.ui.log.LogScreen
import com.garagelog.app.ui.schedule.ScheduleScreen
import com.garagelog.app.ui.settings.ManageVehiclesScreen
import com.garagelog.app.ui.settings.MembersScreen
import com.garagelog.app.ui.settings.SettingsScreen
import com.garagelog.app.ui.theme.GarageFabShape
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.ui.trends.TrendsScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val mainTabs = listOf(AppTab.Dashboard, AppTab.Log, AppTab.Issues, AppTab.Trends)

// Long enough to take a call, look something up, or go read a part number off the shelf and come
// back to the screen you were working on. Only a genuine "put the phone down and moved on" gap
// should land you back on Home.
private const val HOME_RESET_AWAY_THRESHOLD_MS = 10 * 60 * 1000L

private fun AppTab.label(): String = when (this) {
    AppTab.Dashboard -> "Home"
    AppTab.Log -> "Log"
    AppTab.Issues -> "Issues"
    AppTab.Trends -> "Trends"
}

private fun SubScreen.label(): String = when (this) {
    is SubScreen.Schedule -> "Maintenance"
    SubScreen.Settings -> "Settings"
    SubScreen.ManageVehicles -> "Vehicles"
    SubScreen.Members -> "Members"
    is SubScreen.Diagnosis -> "Diagnosis"
    is SubScreen.Chat -> "Ask $ASSISTANT_NAME"
}

private fun SubScreen.icon(): ImageVector = when (this) {
    is SubScreen.Schedule -> Icons.Filled.Build
    SubScreen.Settings -> Icons.Filled.Settings
    SubScreen.ManageVehicles -> Icons.Filled.DirectionsCar
    SubScreen.Members -> Icons.Filled.Group
    is SubScreen.Diagnosis, is SubScreen.Chat -> Icons.Filled.AutoAwesome
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.Dashboard -> Icons.Filled.Home
    AppTab.Log -> Icons.AutoMirrored.Filled.MenuBook
    AppTab.Issues -> Icons.Filled.Warning
    AppTab.Trends -> Icons.AutoMirrored.Filled.ShowChart
}

@Composable
fun GarageLogApp(shell: GarageLogViewModel) {
    val records: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory)
    val bob: BobViewModel = viewModel(factory = BobViewModel.Factory)
    val uiState by shell.uiState.collectAsStateWithLifecycle()
    val syncStatus by shell.syncStatus.collectAsStateWithLifecycle()
    val diagnoses by bob.diagnoses.collectAsStateWithLifecycle()
    val chatMessages by bob.chatMessages.collectAsStateWithLifecycle()
    val hasApiKey by bob.hasApiKey.collectAsStateWithLifecycle()
    val access by shell.access.collectAsStateWithLifecycle()
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
        shell.messages.collectLatest { msg ->
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
                    // Pick up what other people in a shared garage changed while this was closed.
                    shell.syncNow()
                    val stoppedAt = stoppedAtMillis
                    if (stoppedAt == null || System.currentTimeMillis() - stoppedAt > HOME_RESET_AWAY_THRESHOLD_MS) {
                        shell.resetToHome()
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
    // shell.selectTab with the WRONG tab, silently cancelling reorder mode via the "navigated
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
            mainTabs.getOrNull(page)?.let { tab -> if (tab != uiState.currentTab) shell.selectTab(tab) }
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

    // Local val so the `is SubScreen.X` checks below smart-cast to the concrete type.
    val subScreen = uiState.subScreen
    val showingSubScreen = subScreen != null
    val showFab = !showingSubScreen &&
        uiState.currentTab != AppTab.Dashboard && uiState.currentTab != AppTab.Trends &&
        // With no vehicles the FAB adds one, which a Standard member can't.
        (uiState.vehicles.isNotEmpty() || access.canAddVehicles)

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Without this, the system back gesture leaves the app entirely from a sub-screen or an open
    // drawer rather than backing out of it — the same thing the on-screen affordance does.
    BackHandler(enabled = showingSubScreen || reorderMode || drawerState.isOpen) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            reorderMode -> reorderMode = false
            else -> shell.closeSubScreen()
        }
    }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only from the drawer's own button: an edge-swipe would fight the tab pager's swipe,
        // which is the primary way this app is navigated.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            GarageDrawer(
                onOpenSchedule = { shell.openSchedule() },
                onOpenManageVehicles = { shell.openManageVehicles() },
                onOpenSettings = { shell.openSettings() },
                onOpenMembers = if (access.canManageMembers) { { shell.openMembers() } } else null,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
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
                            subScreen != null -> subScreen.label()
                            else -> liveTab.label()
                        }
                        val headerIcon = when {
                            reorderMode -> Icons.Filled.Home
                            subScreen != null -> subScreen.icon()
                            else -> liveTab.icon()
                        }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                            ) {
                                // One slot that flips between "open the menu" and "back out of
                                // here", so a sub-screen never offers a menu button where the
                                // way back belongs.
                                if (showingSubScreen || reorderMode) {
                                    IconButton(onClick = {
                                        if (reorderMode) reorderMode = false else shell.closeSubScreen()
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                                    }
                                }
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        // The filter row doesn't do anything useful on every screen —
                        // VehicleReorderScreen always shows every vehicle regardless of it, and
                        // Settings/Vehicles aren't per-vehicle at all — so hiding it there avoids
                        // implying a selection those screens would just ignore.
                        if (!reorderMode && (subScreen?.usesVehicleFilter != false)) {
                            VehiclePickerRow(
                                vehicles = uiState.vehicles,
                                // A chat belongs to one vehicle; its chip is the one lit.
                                activeVehicleId = (subScreen as? SubScreen.Chat)?.vehicleId ?: uiState.activeVehicleId,
                                onSelect = shell::selectVehicle,
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
                        onClick = { shell.resetToHome() },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Log && !showingSubScreen,
                        onClick = { shell.selectTab(AppTab.Log) },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        label = { Text("Log") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Issues && !showingSubScreen,
                        onClick = { shell.selectTab(AppTab.Issues) },
                        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                        label = { Text("Issues") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.Trends && !showingSubScreen,
                        onClick = { shell.selectTab(AppTab.Trends) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
                        label = { Text("Trends") },
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
                    onReorderVehicles = records::reorderVehicles,
                    onDone = { reorderMode = false },
                )
                subScreen is SubScreen.Settings -> SettingsScreen(uiState = uiState, onOpenMembers = { shell.openMembers() })
                subScreen is SubScreen.Members -> MembersScreen(vehicles = uiState.vehicles)
                subScreen is SubScreen.ManageVehicles -> ManageVehiclesScreen(
                    vehicles = uiState.vehicles,
                    onAddVehicle = if (access.canAddVehicles) { { activeSheet = Sheet.VehicleForm(null) } } else null,
                    onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                    onReorderVehicles = {
                        shell.resetToHome()
                        reorderMode = true
                    },
                )
                subScreen is SubScreen.Diagnosis -> {
                    val issue = uiState.issues.find { it.id == subScreen.issueId }
                    // The issue can vanish underneath this screen (deleted from another tab, or a
                    // sync merge), so fall back to closing rather than rendering a half-empty page.
                    if (issue == null) {
                        LaunchedEffect(Unit) { shell.closeSubScreen() }
                    } else {
                        AiDiagnosisScreen(
                            issue = issue,
                            vehicleName = uiState.vehicleName(issue.vehicleId),
                            diagnosis = diagnoses[issue.id],
                            runsFlow = bob.runs,
                            hasApiKey = hasApiKey,
                            sourcesFor = bob::decodeSources,
                            onRun = { bob.runDiagnosis(issue) },
                            onStop = { bob.cancelDiagnosis(issue.id) },
                            onOpenSettings = shell::openSettings,
                            onAskFollowUp = if (access.canChatWithBob) { { shell.openChat(issue.vehicleId, issue.id) } } else null,
                        )
                    }
                }
                subScreen is SubScreen.Chat -> {
                    val vehicle = uiState.vehicles.find { it.id == subScreen.vehicleId }
                    if (vehicle == null) {
                        LaunchedEffect(Unit) { shell.closeSubScreen() }
                    } else {
                        val issueId = subScreen.issueId
                        val followedIssue = issueId?.let { id -> uiState.issues.find { it.id == id } }
                        AiChatScreen(
                            vehicleLabel = followedIssue?.let { "${vehicle.name} · ${it.title}" } ?: vehicle.name,
                            messages = chatMessages.filter { it.vehicleId == vehicle.id && it.issueId == issueId },
                            runsFlow = bob.runs,
                            runKey = AiChatMessageEntity.threadKey(vehicle.id, issueId),
                            hasApiKey = hasApiKey,
                            sourcesFor = bob::decodeSources,
                            onSend = {
                                bob.sendChatMessage(vehicle.id, issueId, it, vehicle.name)
                            },
                            onStop = { bob.cancelChat(vehicle.id, issueId) },
                            onClear = { bob.clearChat(vehicle.id, issueId) },
                            onOpenSettings = shell::openSettings,
                        )
                    }
                }
                subScreen is SubScreen.Schedule -> ScheduleScreen(
                    uiState = uiState,
                    onBack = shell::closeSubScreen,
                    onEdit = { activeSheet = Sheet.ScheduleForm(it) },
                    onAddNew = if (access.canEditSchedules) { { activeSheet = Sheet.ScheduleForm(null) } } else null,
                    onMarkDone = records::markScheduleDoneToday,
                    onCopySchedule = if (access.canEditSchedules) { { from, to -> records.copySchedulesToVehicle(from, to) } } else null,
                    onLogSchedule = { sched ->
                        activeSheet = newLogSheet(
                            vehicleId = sched.vehicleId,
                            category = LogCategory.Routine,
                            task = sched.taskName,
                            mileage = uiState.vehicles.find { it.id == sched.vehicleId }?.miles,
                            fulfillsScheduleId = sched.id,
                        )
                    },
                    onSuggestWithBob = if (access.canEditSchedules && access.canChatWithBob) { { activeSheet = Sheet.BobSchedule(it) } } else null,
                    focusId = subScreen.focusId,
                )
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (mainTabs[page]) {
                        AppTab.Dashboard -> DashboardScreen(
                            uiState = uiState,
                            syncStatus = syncStatus,
                            onRefresh = shell::syncNow,
                            onEditVehicle = { activeSheet = Sheet.VehicleForm(it) },
                            onAddVehicle = if (access.canAddVehicles) { { activeSheet = Sheet.VehicleForm(null) } } else null,
                            onOpenSchedule = shell::openSchedule,
                            onUpdateMileage = records::updateMileage,
                            onOpenVehicleTab = shell::openVehicleTab,
                            onAddFueling = { vehicle ->
                                // Mileage deliberately blank, not the vehicle's current miles: a fill-up
                                // is a fresh odometer reading, and prefilling the last one made it too
                                // easy to save a stale number without noticing.
                                activeSheet = newLogSheet(vehicle.id, LogCategory.Fuel, task = "Fuel", mileage = null)
                            },
                            onSetVehiclePhoto = records::setVehiclePhoto,
                            onAskClaude = if (access.canChatWithBob) { { shell.openChat(it.id) } } else null,
                        )
                        AppTab.Log -> LogScreen(
                            uiState = uiState,
                            onItemClick = { activeSheet = Sheet.LogForm(it) },
                            onDelete = { records.deleteLog(it.id) },
                            canDelete = { access.canDeleteEntry(it.createdBy) },
                        )
                        AppTab.Issues -> IssuesScreen(
                            uiState = uiState,
                            diagnosedIssueIds = diagnoses.keys,
                            onItemClick = { activeSheet = Sheet.IssueForm(it) },
                            onDiagnose = { shell.openDiagnosis(it.id) },
                            onDelete = { records.deleteIssue(it.id) },
                            canDelete = { access.canDeleteEntry(it.createdBy) },
                        )
                        AppTab.Trends -> TrendsScreen(uiState = uiState)
                    }
                }
            }
        }
    }
    }

    AppSheets(sheet = activeSheet, uiState = uiState, access = access, records = records, onShow = { activeSheet = it })
}
