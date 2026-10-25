package com.garagelog.app.ui.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.formatMiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun statusRank(status: DueStatus): Int = when (status) {
    DueStatus.OVERDUE -> 0
    DueStatus.DUE_SOON -> 1
    DueStatus.OK -> 2
    DueStatus.UNKNOWN -> 3
}

@Composable
fun ScheduleScreen(
    uiState: GarageLogUiState,
    onBack: () -> Unit,
    onEdit: (MaintenanceScheduleEntity) -> Unit,
    // Null = hidden: schedules are admin-only for this person (shared-garage permissions).
    onAddNew: (() -> Unit)?,
    onMarkDone: (MaintenanceScheduleEntity) -> Unit,
    onCopySchedule: ((sourceVehicleId: String, targetVehicleId: String) -> Unit)?,
    // A completed maintenance item is, functionally, a routine log entry — confirming "mark
    // done" opens the log form pre-filled with this schedule's task so the actual service
    // (cost, notes, photos) can be recorded, not just the tracker's due date silently bumped.
    onLogSchedule: (MaintenanceScheduleEntity) -> Unit,
    onSuggestWithBob: ((VehicleEntity) -> Unit)?,
    /** A schedule item to scroll to and highlight on arrival, e.g. an overdue one tapped on Home. */
    focusId: String? = null,
    /** Bob can pick the best how-to video (admins; Standard members get the free search only). */
    canAskBob: Boolean = false,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    var confirmingSchedule by remember { mutableStateOf<MaintenanceScheduleEntity?>(null) }
    var justCompletedId by remember { mutableStateOf<String?>(null) }
    var copyFromVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var howTo by remember { mutableStateOf<Pair<VehicleEntity, MaintenanceScheduleEntity>?>(null) }
    val listState = rememberLazyListState()

    // Arriving from Home with an item to show: scroll the list to its vehicle's card first. The
    // row's own bringIntoView (below) can only move it once that card is composed, and a lazy list
    // doesn't compose a card that starts off screen, so an item on the second or third vehicle
    // never came into view.
    val focusVehicleIndex = focusId?.let { id ->
        val vehicleId = uiState.schedules.find { it.id == id }?.vehicleId
        val position = vehicles.indexOfFirst { it.id == vehicleId }
        if (position < 0) null else 1 + vehicles.take(position).sumOf { v -> if (uiState.schedules.any { it.vehicleId == v.id }) 2 else 1 }
    }
    LaunchedEffect(focusId, focusVehicleIndex) {
        focusVehicleIndex?.let { listState.scrollToItem(it) }
    }

    LaunchedEffect(justCompletedId) {
        if (justCompletedId != null) {
            delay(800)
            justCompletedId = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
        ) {
            onAddNew?.let { add -> IconButton(onClick = add) { Icon(Icons.Filled.Add, contentDescription = "Add schedule") } }
        }

        LazyColumn(state = listState, contentPadding = GarageDimens.subScreenContentPadding) {
            // Always one item here (even if empty), so focusVehicleIndex can count from it.
            item {
                if (vehicles.none { v -> uiState.schedules.any { it.vehicleId == v.id } }) {
                    EmptyState(
                        if (onAddNew != null) {
                            "No maintenance intervals tracked yet. Tap + to add one, or have $ASSISTANT_NAME suggest a schedule below."
                        } else {
                            "No maintenance intervals tracked yet."
                        },
                        icon = Icons.Filled.Build,
                    )
                }
            }
            vehicles.forEach { v ->
                // Most-urgent-first: grouped by status (overdue, then due soon, then on track,
                // then untracked), and within a group by soonest-due mileage — falling back to
                // the item's own interval length when there's no service history yet to compute
                // an actual remaining-mileage figure from.
                val schedules = uiState.schedules.filter { it.vehicleId == v.id }
                    .sortedWith(
                        compareBy(
                            { statusRank(computeDueInfo(it, v.miles, v.isSevereDuty).status) },
                            { computeDueInfo(it, v.miles, v.isSevereDuty).remainingMiles ?: it.intervalMiles ?: Int.MAX_VALUE },
                        ),
                    )
                // Every vehicle gets its header, even with nothing tracked yet — that's exactly when
                // "Suggest with Bob" is most useful.
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.weight(1f)) { SectionTitle(v.name) }
                        onSuggestWithBob?.let { suggest -> ActionLink("Suggest with $ASSISTANT_NAME", onClick = { suggest(v) }) }
                        if (onCopySchedule != null && uiState.vehicles.size > 1 && schedules.isNotEmpty()) {
                            ActionLink("Copy…", onClick = { copyFromVehicle = v })
                        }
                    }
                }
                if (schedules.isNotEmpty()) {
                    item {
                        GarageCard {
                            schedules.forEachIndexed { index, sched ->
                                ScheduleRow(
                                    sched,
                                    v.miles,
                                    v.isSevereDuty,
                                    onEdit = { onEdit(sched) },
                                    onMarkDone = { confirmingSchedule = sched },
                                    justCompleted = sched.id == justCompletedId,
                                    focused = sched.id == focusId,
                                    onHowTo = { howTo = v to sched },
                                )
                                if (index != schedules.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmingSchedule?.let { sched ->
        ConfirmDialog(
            title = "Mark done?",
            message = "Mark \"${sched.taskName}\" as done today?",
            confirmLabel = "Mark done",
            confirmColor = garageColors.ok,
            onConfirm = {
                onMarkDone(sched)
                justCompletedId = sched.id
                onLogSchedule(sched)
            },
            onDismiss = { confirmingSchedule = null },
        )
    }

    howTo?.let { (vehicle, sched) ->
        HowToDialog(vehicle = vehicle, task = sched.taskName, canAskBob = canAskBob, onDismiss = { howTo = null })
    }

    copyFromVehicle?.let { source ->
        CopyScheduleDialog(
            vehicles = uiState.vehicles.filter { it.id != source.id },
            onPick = { target -> onCopySchedule?.invoke(source.id, target.id); copyFromVehicle = null },
            onDismiss = { copyFromVehicle = null },
        )
    }
}

@Composable
private fun CopyScheduleDialog(vehicles: List<VehicleEntity>, onPick: (VehicleEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy schedule to…") },
        text = {
            Column {
                vehicles.forEach { v ->
                    Text(
                        v.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScheduleRow(
    schedule: MaintenanceScheduleEntity,
    currentMiles: Int?,
    severeDuty: Boolean,
    onEdit: () -> Unit,
    onMarkDone: () -> Unit,
    justCompleted: Boolean,
    focused: Boolean = false,
    onHowTo: () -> Unit = {},
) {
    val info = computeDueInfo(schedule, currentMiles, severeDuty)
    val tone = when (info.status) {
        DueStatus.OVERDUE -> PillTone.Open
        DueStatus.DUE_SOON -> PillTone.Progress
        DueStatus.OK -> PillTone.Resolved
        DueStatus.UNKNOWN -> PillTone.Upcoming
    }

    val highlight = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(justCompleted) {
        if (justCompleted) {
            highlight.snapTo(1f)
            launch { highlight.animateTo(0f, animationSpec = tween(800, easing = LinearOutSlowInEasing)) }
            launch {
                pop.animateTo(1.03f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
                pop.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
    }

    // Arriving from a tapped item on Home: bring this row on screen and pulse it, so it's clear
    // which one was meant (rows for one vehicle share a card, so the list can't scroll to it alone).
    val focusGlow = remember { Animatable(0f) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            delay(150) // let the list lay out first
            bringIntoView.bringIntoView()
            // Held, then faded slowly: a quick pulse was easy to miss while the list was still moving.
            focusGlow.snapTo(1f)
            delay(1200)
            focusGlow.animateTo(0f, animationSpec = tween(2400, easing = LinearOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.bringIntoViewRequester(bringIntoView)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clickable(onClick = onEdit)
                .padding(vertical = 11.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(schedule.taskName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PillBadge(
                        text = when (info.status) {
                            DueStatus.OVERDUE -> "Overdue"
                            DueStatus.DUE_SOON -> "Due soon"
                            DueStatus.OK -> "On track"
                            DueStatus.UNKNOWN -> "Not tracked"
                        },
                        tone = tone,
                    )
                }
                Text(info.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                val interval = buildList {
                    schedule.intervalMiles?.let { add("every ${formatMiles(it)}") }
                    schedule.intervalMonths?.let { add("every $it mo") }
                }.joinToString(" · ")
                if (interval.isNotBlank()) {
                    Text(interval, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    ActionLink("Mark done today", onClick = onMarkDone)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onHowTo) {
                        Icon(Icons.Filled.OndemandVideo, contentDescription = "How-to video", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (focusGlow.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(garageColors.info.copy(alpha = 0.24f * focusGlow.value)),
            )
        }
        if (highlight.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(garageColors.ok.copy(alpha = 0.22f * highlight.value)),
            )
        }
    }
}
