package com.garagelog.app.ui.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay

@Composable
fun ScheduleScreen(
    uiState: GarageLogUiState,
    onBack: () -> Unit,
    onEdit: (MaintenanceScheduleEntity) -> Unit,
    onAddNew: () -> Unit,
    onMarkDone: (MaintenanceScheduleEntity) -> Unit,
    onCopySchedule: (sourceVehicleId: String, targetVehicleId: String) -> Unit,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    var confirmingSchedule by remember { mutableStateOf<MaintenanceScheduleEntity?>(null) }
    var justCompletedId by remember { mutableStateOf<String?>(null) }
    var copyFromVehicle by remember { mutableStateOf<VehicleEntity?>(null) }

    LaunchedEffect(justCompletedId) {
        if (justCompletedId != null) {
            delay(800)
            justCompletedId = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Maintenance schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddNew) { Icon(Icons.Filled.Add, contentDescription = "Add schedule") }
        }

        LazyColumn(contentPadding = GarageDimens.subScreenContentPadding) {
            if (vehicles.none { v -> uiState.schedules.any { it.vehicleId == v.id } }) {
                item { EmptyState("No maintenance intervals tracked yet. Tap + to add one, like \"Oil change every 5,000 mi.\"") }
            }
            vehicles.forEach { v ->
                val schedules = uiState.schedules.filter { it.vehicleId == v.id }
                if (schedules.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SectionTitle(v.name)
                            if (uiState.vehicles.size > 1) {
                                ActionLink("Copy to vehicle…", onClick = { copyFromVehicle = v })
                            }
                        }
                    }
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
            onConfirm = { onMarkDone(sched); justCompletedId = sched.id },
            onDismiss = { confirmingSchedule = null },
        )
    }

    copyFromVehicle?.let { source ->
        CopyScheduleDialog(
            vehicles = uiState.vehicles.filter { it.id != source.id },
            onPick = { target -> onCopySchedule(source.id, target.id); copyFromVehicle = null },
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
) {
    val info = computeDueInfo(schedule, currentMiles, severeDuty)
    val tone = when (info.status) {
        DueStatus.OVERDUE -> PillTone.Open
        DueStatus.DUE_SOON -> PillTone.Progress
        DueStatus.OK -> PillTone.Resolved
        DueStatus.UNKNOWN -> PillTone.Upcoming
    }

    val highlight = remember { Animatable(0f) }
    LaunchedEffect(justCompleted) {
        if (justCompleted) {
            highlight.snapTo(1f)
            highlight.animateTo(0f, animationSpec = tween(800, easing = LinearOutSlowInEasing))
        }
    }

    Box {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 11.dp)) {
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
                    schedule.intervalMiles?.let { add("every $it mi") }
                    schedule.intervalMonths?.let { add("every $it mo") }
                }.joinToString(" · ")
                if (interval.isNotBlank()) {
                    Text(interval, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                ActionLink("Mark done today", onClick = onMarkDone, modifier = Modifier.padding(top = 6.dp))
            }
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
