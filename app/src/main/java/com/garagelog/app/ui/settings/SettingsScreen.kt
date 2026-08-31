package com.garagelog.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.todayIso
import com.garagelog.app.data.entity.VehicleEntity

@Composable
fun SettingsScreen(
    uiState: GarageLogUiState,
    viewModel: GarageLogViewModel,
    onAddVehicle: () -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCostTrend: () -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { viewModel.exportBackup(it) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let { viewModel.importBackup(it) }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp)) {
        item {
            AccountSection(viewModel = viewModel, modifier = Modifier.padding(bottom = 12.dp))
        }

        item {
            GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Text("About this data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stored on this device, and synced to Drive automatically once you're signed in above. The JSON export below is still worth keeping as a manual backup either way.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatGrid(
                    listOf(
                        uiState.vehicles.size.toString() to "vehicles",
                        uiState.logs.size.toString() to "log entries",
                        uiState.issues.size.toString() to "issues",
                    ),
                )
            }
        }

        item {
            GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Insights", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    OutlinedButton(onClick = onOpenSchedule, modifier = Modifier.weight(1f)) { Text("Maintenance schedule") }
                    OutlinedButton(onClick = onOpenCostTrend, modifier = Modifier.weight(1f)) { Text("Cost trend") }
                }
            }
        }

        item {
            GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Manage vehicles", style = MaterialTheme.typography.titleMedium)
                uiState.vehicles.forEachIndexed { index, v ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onEditVehicle(v) }.padding(vertical = 10.dp),
                    ) {
                        Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(v.year?.toString(), v.make.ifBlank { null }, v.model.ifBlank { null }).joinToString(" "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (index != uiState.vehicles.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                OutlinedButton(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("+ Add vehicle")
                }
            }
        }

        item {
            GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export a JSON backup you can keep in a cloud drive, and re-import it any time (including on a new phone).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Button(
                        onClick = { exportLauncher.launch("garage-log-backup-${todayIso()}.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export backup") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import backup") }
                }
            }
        }

        item {
            GarageCard {
                Text("Reset", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wipes all data on this device and reloads the original starting seed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text("Reset to seed data", color = garageColors.alarmText) }
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Reset to seed data?",
            message = "This will erase all current data on this device and restore the original seed data.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.resetToSeed() },
            onDismiss = { showResetConfirm = false },
        )
    }
}
