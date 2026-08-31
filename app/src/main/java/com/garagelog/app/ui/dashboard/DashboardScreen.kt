package com.garagelog.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.theme.Accent
import com.garagelog.app.ui.theme.TextDim
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney

@Composable
fun DashboardScreen(
    uiState: GarageLogUiState,
    onEditVehicle: (VehicleEntity) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCostTrend: () -> Unit,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp)) {
        if (vehicles.isEmpty()) {
            item { EmptyState("No vehicles yet. Tap + to add one.") }
        }
        items(vehicles, key = { it.id }) { v ->
            VehicleDashboardCard(uiState, v, onEditVehicle)
            Spacer(Modifier.padding(bottom = 12.dp))
        }
        if (vehicles.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(onClick = onOpenSchedule, modifier = Modifier.weight(1f)) { Text("Maintenance") }
                    OutlinedButton(onClick = onOpenCostTrend, modifier = Modifier.weight(1f)) { Text("Cost trend") }
                }
            }
        }
    }
}

@Composable
private fun VehicleDashboardCard(uiState: GarageLogUiState, v: VehicleEntity, onEditVehicle: (VehicleEntity) -> Unit) {
    val logs = uiState.logs.filter { it.vehicleId == v.id }
    val openIssues = uiState.issues.filter { it.vehicleId == v.id && it.status != IssueStatus.Resolved.label }
    val totalSpent = logs.sumOf { it.cost ?: 0.0 }
    val lastLog = logs.maxByOrNull { it.date }
    val dueItems = uiState.schedules.filter { it.vehicleId == v.id }
        .map { it to computeDueInfo(it, v.miles) }
        .filter { it.second.status == DueStatus.OVERDUE || it.second.status == DueStatus.DUE_SOON }

    GarageCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val title = buildString {
                append(listOfNotNull(v.year?.toString(), v.make.ifBlank { null }, v.model.ifBlank { null }).joinToString(" "))
                if (v.name.isNotBlank() && v.name != v.model) append(" \"${v.name}\"")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "Edit",
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { onEditVehicle(v) },
            )
        }
        val subtitle = listOfNotNull(v.engine.ifBlank { null }, v.drivetrain.ifBlank { null }).joinToString(" · ")
        if (subtitle.isNotBlank()) Text(subtitle, color = TextDim, style = MaterialTheme.typography.bodyMedium)
        if (v.role.isNotBlank()) Text(v.role, color = TextDim, style = MaterialTheme.typography.bodyMedium)

        StatGrid(
            listOf(
                formatMiles(v.miles) to "current miles",
                openIssues.size.toString() to "open issues",
                formatMoney(totalSpent) to "logged spend",
            ),
        )

        if (dueItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                dueItems.forEach { (sched, info) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        PillBadge(
                            text = if (info.status == DueStatus.OVERDUE) "Overdue" else "Due soon",
                            tone = if (info.status == DueStatus.OVERDUE) PillTone.Open else PillTone.Progress,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${sched.taskName} — ${info.label}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                }
            }
        }

        Text(
            text = lastLog?.let { "Last logged: ${formatDate(it.date)} — ${it.task}" } ?: "No log entries yet.",
            color = TextDim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (v.notes.isNotBlank()) {
            var showDetails by remember(v.id) { mutableStateOf(false) }
            Text(
                text = if (showDetails) "Hide details" else "Show details",
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp).clickable { showDetails = !showDetails },
            )
            if (showDetails) {
                Text(v.notes, color = TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
