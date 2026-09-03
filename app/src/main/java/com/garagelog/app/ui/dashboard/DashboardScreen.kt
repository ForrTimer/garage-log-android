package com.garagelog.app.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.sync.SyncStatus
import com.garagelog.app.ui.AppTab
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: GarageLogUiState,
    syncStatus: SyncStatus,
    onRefresh: () -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onAddVehicle: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCostTrend: () -> Unit,
    onUpdateMileage: (VehicleEntity, Int) -> Unit,
    onOpenVehicleTab: (String, AppTab) -> Unit,
    onOpenVehicleCostTrend: (String) -> Unit,
    onSetVehiclePhoto: (VehicleEntity, Uri) -> Unit,
    onReorderVehicles: (List<String>) -> Unit,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles
    val canReorder = uiState.activeVehicleId == null && vehicles.size > 1
    val baseOrderIds = vehicles.map { it.id }
    val haptics = LocalHapticFeedback.current

    // While the user is actively dragging (or waiting for the reorder to round-trip through the
    // database), the dragged arrangement lives here instead of being derived from `vehicles` —
    // otherwise the live-reordered list would fight the not-yet-updated upstream order. Once the
    // database confirms the same order, this drops back to null so `vehicles` is the sole source
    // of truth again.
    var liveOrderIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(baseOrderIds) {
        if (liveOrderIds != null && liveOrderIds == baseOrderIds) liveOrderIds = null
    }
    val orderIds = liveOrderIds ?: baseOrderIds

    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val orderedVehicles = orderIds.mapNotNull { id -> vehicles.find { it.id == id } }

    PullToRefreshBox(isRefreshing = syncStatus.isSyncing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = GarageDimens.listContentPadding) {
            if (vehicles.isEmpty()) {
                item {
                    EmptyState("No vehicles yet.", icon = Icons.Filled.DirectionsCar)
                    OutlinedButton(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth()) { Text("Add a vehicle") }
                }
            }
            items(orderedVehicles, key = { it.id }) { v ->
                Box(
                    modifier = Modifier
                        .zIndex(if (v.id == draggingId) 1f else 0f)
                        .graphicsLayer { translationY = if (v.id == draggingId) dragOffset else 0f },
                ) {
                    VehicleDashboardCard(
                        uiState = uiState,
                        v = v,
                        onEditVehicle = onEditVehicle,
                        onOpenSchedule = onOpenSchedule,
                        onUpdateMileage = onUpdateMileage,
                        onOpenVehicleTab = onOpenVehicleTab,
                        onOpenVehicleCostTrend = onOpenVehicleCostTrend,
                        onSetVehiclePhoto = onSetVehiclePhoto,
                        showDragHandle = canReorder,
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingId = v.id
                            dragOffset = 0f
                            liveOrderIds = baseOrderIds
                        },
                        onDrag = onDrag@{ delta ->
                            dragOffset += delta
                            val id = draggingId ?: return@onDrag
                            val current = liveOrderIds ?: return@onDrag
                            val itemsInfo = listState.layoutInfo.visibleItemsInfo
                            val dragged = itemsInfo.firstOrNull { it.key == id } ?: return@onDrag
                            val draggedCenter = dragged.offset + dragOffset + dragged.size / 2
                            val target = itemsInfo.firstOrNull { other ->
                                other.key != id && draggedCenter > other.offset && draggedCenter < other.offset + other.size
                            }
                            if (target != null) {
                                val from = current.indexOf(id)
                                val to = current.indexOf(target.key as String)
                                if (from != -1 && to != -1 && from != to) {
                                    dragOffset += dragged.offset - target.offset
                                    liveOrderIds = current.toMutableList().apply { add(to, removeAt(from)) }
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            dragOffset = 0f
                            liveOrderIds?.let { onReorderVehicles(it) }
                        },
                    )
                }
                Spacer(Modifier.padding(bottom = 12.dp))
            }
        }
        if (vehicles.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = GarageDimens.screenHorizontal, vertical = 12.dp),
            ) {
                OutlinedButton(onClick = onOpenSchedule, modifier = Modifier.weight(1f)) { Text("Maintenance") }
                OutlinedButton(onClick = onOpenCostTrend, modifier = Modifier.weight(1f)) { Text("Cost trend") }
            }
        }
    }
    }
}

@Composable
private fun VehicleDashboardCard(
    uiState: GarageLogUiState,
    v: VehicleEntity,
    onEditVehicle: (VehicleEntity) -> Unit,
    onOpenSchedule: () -> Unit,
    onUpdateMileage: (VehicleEntity, Int) -> Unit,
    onOpenVehicleTab: (String, AppTab) -> Unit,
    onOpenVehicleCostTrend: (String) -> Unit,
    onSetVehiclePhoto: (VehicleEntity, Uri) -> Unit,
    showDragHandle: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val logs = uiState.logs.filter { it.vehicleId == v.id }
    val openIssues = uiState.issues.filter { it.vehicleId == v.id && it.status != IssueStatus.Resolved.label }
    val totalSpent = logs.sumOf { it.cost ?: 0.0 }
    val lastLog = logs.maxByOrNull { it.date }
    val schedulesForVehicle = uiState.schedules.filter { it.vehicleId == v.id }
    val dueItems = schedulesForVehicle
        .map { it to computeDueInfo(it, v.miles, v.isSevereDuty) }
        .filter { it.second.status == DueStatus.OVERDUE || it.second.status == DueStatus.DUE_SOON }
        .sortedByDescending { it.second.status == DueStatus.OVERDUE }

    var showMileageDialog by remember(v.id) { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onSetVehiclePhoto(v, uri)
    }

    GarageCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (v.photoPath != null) {
                    AsyncImage(
                        model = File(v.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = "Add a photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            val title = if (v.name.isNotBlank() && v.name != v.model) {
                v.name
            } else {
                listOfNotNull(v.year?.toString(), v.model.ifBlank { null }).joinToString(" ")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (showDragHandle) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .pointerInput(v.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount.y) },
                            )
                        },
                )
            }
            ActionLink("Edit", onClick = { onEditVehicle(v) })
        }

        // Maintenance alerts lead the card — this is the thing an owner actually opens the app
        // to check, so it shouldn't be buried below static vehicle specs.
        Column(modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clickable(onClick = onOpenSchedule)) {
            if (dueItems.isNotEmpty()) {
                dueItems.forEach { (sched, info) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        PillBadge(
                            text = if (info.status == DueStatus.OVERDUE) "Overdue" else "Due soon",
                            tone = if (info.status == DueStatus.OVERDUE) PillTone.Open else PillTone.Progress,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${sched.taskName} — ${info.label}", style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
                    }
                }
            } else if (schedulesForVehicle.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    PillBadge(text = "On track", tone = PillTone.Resolved)
                    Spacer(Modifier.width(8.dp))
                    Text("All maintenance up to date", style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
                }
            } else {
                Text(
                    "No maintenance tracked yet — tap to add a schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = garageColors.textMuted,
                )
            }
        }

        StatGrid(
            listOf(
                formatMiles(v.miles) to "current miles",
                openIssues.size.toString() to "open issues",
                formatMoney(totalSpent) to "logged spend",
            ),
            onItemClick = { index ->
                when (index) {
                    0 -> showMileageDialog = true
                    1 -> onOpenVehicleTab(v.id, AppTab.Issues)
                    2 -> onOpenVehicleCostTrend(v.id)
                }
            },
        )

        ActionLink("Update mileage", onClick = { showMileageDialog = true }, modifier = Modifier.padding(top = 8.dp))

        Text(
            text = lastLog?.let { "Last logged: ${formatDate(it.date)} — ${it.task}" } ?: "No log entries yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )

        var showDetails by remember(v.id) { mutableStateOf(false) }
        ActionLink(
            text = if (showDetails) "Hide vehicle details" else "Show vehicle details",
            onClick = { showDetails = !showDetails },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (showDetails) {
            val identity = listOfNotNull(v.year?.toString(), v.make.ifBlank { null }, v.model.ifBlank { null }).joinToString(" ")
            val subtitle = listOfNotNull(v.engine.ifBlank { null }, v.drivetrain.ifBlank { null }).joinToString(" · ")
            Column(modifier = Modifier.padding(top = 6.dp)) {
                if (identity.isNotBlank()) Text(identity, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (v.role.isNotBlank()) Text(v.role, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (v.notes.isNotBlank()) Text(v.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showMileageDialog) {
        UpdateMileageDialog(
            currentMiles = v.miles,
            onConfirm = { newMiles -> onUpdateMileage(v, newMiles) },
            onDismiss = { showMileageDialog = false },
        )
    }
}

@Composable
private fun UpdateMileageDialog(currentMiles: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentMiles?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update mileage") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Current mileage") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.trim().toIntOrNull()?.let(onConfirm)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
