package com.garagelog.app.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.Accent
import com.garagelog.app.ui.theme.Border
import com.garagelog.app.ui.theme.TextDim
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo

@Composable
fun ScheduleScreen(
    uiState: GarageLogUiState,
    onBack: () -> Unit,
    onEdit: (MaintenanceScheduleEntity) -> Unit,
    onAddNew: () -> Unit,
    onMarkDone: (MaintenanceScheduleEntity) -> Unit,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Maintenance schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddNew) { Icon(Icons.Filled.Add, contentDescription = "Add schedule") }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
            if (vehicles.none { v -> uiState.schedules.any { it.vehicleId == v.id } }) {
                item { EmptyState("No maintenance intervals tracked yet. Tap + to add one, like \"Oil change every 5,000 mi.\"") }
            }
            vehicles.forEach { v ->
                val schedules = uiState.schedules.filter { it.vehicleId == v.id }
                if (schedules.isNotEmpty()) {
                    item { SectionTitle(v.name) }
                    item {
                        GarageCard {
                            schedules.forEachIndexed { index, sched ->
                                ScheduleRow(sched, v.miles, onEdit = { onEdit(sched) }, onMarkDone = { onMarkDone(sched) })
                                if (index != schedules.lastIndex) HorizontalDivider(color = Border)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: MaintenanceScheduleEntity, currentMiles: Int?, onEdit: () -> Unit, onMarkDone: () -> Unit) {
    val info = computeDueInfo(schedule, currentMiles)
    val tone = when (info.status) {
        DueStatus.OVERDUE -> PillTone.Open
        DueStatus.DUE_SOON -> PillTone.Progress
        DueStatus.OK -> PillTone.Resolved
        DueStatus.UNKNOWN -> PillTone.Upcoming
    }
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
            Text(info.label, color = TextDim, style = MaterialTheme.typography.bodySmall)
            val interval = buildList {
                schedule.intervalMiles?.let { add("every $it mi") }
                schedule.intervalMonths?.let { add("every $it mo") }
            }.joinToString(" · ")
            if (interval.isNotBlank()) Text(interval, color = TextDim, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onMarkDone, contentPadding = PaddingValues(0.dp)) {
                Text("Mark done today", color = Accent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
