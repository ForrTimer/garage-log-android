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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.AppTab
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney

@Composable
fun DashboardScreen(
    uiState: GarageLogUiState,
    onEditVehicle: (VehicleEntity) -> Unit,
    onAddVehicle: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCostTrend: () -> Unit,
    onUpdateMileage: (VehicleEntity, Int) -> Unit,
    onOpenVehicleTab: (String, AppTab) -> Unit,
    onOpenVehicleCostTrend: (String) -> Unit,
) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp)) {
        if (vehicles.isEmpty()) {
            item {
                EmptyState("No vehicles yet.")
                OutlinedButton(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth()) { Text("Add a vehicle") }
            }
        }
        items(vehicles, key = { it.id }) { v ->
            VehicleDashboardCard(uiState, v, onEditVehicle, onOpenSchedule, onUpdateMileage, onOpenVehicleTab, onOpenVehicleCostTrend)
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
private fun VehicleDashboardCard(
    uiState: GarageLogUiState,
    v: VehicleEntity,
    onEditVehicle: (VehicleEntity) -> Unit,
    onOpenSchedule: () -> Unit,
    onUpdateMileage: (VehicleEntity, Int) -> Unit,
    onOpenVehicleTab: (String, AppTab) -> Unit,
    onOpenVehicleCostTrend: (String) -> Unit,
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

    GarageCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val title = buildString {
                append(listOfNotNull(v.year?.toString(), v.make.ifBlank { null }, v.model.ifBlank { null }).joinToString(" "))
                if (v.name.isNotBlank() && v.name != v.model) append(" \"${v.name}\"")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "Edit",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onEditVehicle(v) },
            )
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
                    textDecoration = TextDecoration.Underline,
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

        Text(
            "Update mileage",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 8.dp).clickable { showMileageDialog = true },
        )

        Text(
            text = lastLog?.let { "Last logged: ${formatDate(it.date)} — ${it.task}" } ?: "No log entries yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )

        val subtitle = listOfNotNull(v.engine.ifBlank { null }, v.drivetrain.ifBlank { null }).joinToString(" · ")
        val hasDetails = subtitle.isNotBlank() || v.role.isNotBlank() || v.notes.isNotBlank()
        if (hasDetails) {
            var showDetails by remember(v.id) { mutableStateOf(false) }
            Text(
                text = if (showDetails) "Hide vehicle details" else "Show vehicle details",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(top = 10.dp).clickable { showDetails = !showDetails },
            )
            if (showDetails) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (v.role.isNotBlank()) Text(v.role, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (v.notes.isNotBlank()) Text(v.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
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
