package com.garagelog.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.ReminderCadence
import com.garagelog.app.data.seed.SampleData
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.ReadOnlyDropdownField
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.components.TimeField
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.ui.theme.garageSwitchColors
import com.garagelog.app.util.dayOfWeekName
import com.garagelog.app.util.monthName
import com.garagelog.app.util.todayIso
import java.util.Calendar

@Composable
fun SettingsScreen(
    uiState: GarageLogUiState,
    onOpenMembers: () -> Unit,
    onShowIntro: () -> Unit,
    onResetTips: () -> Unit,
    onAddSampleData: () -> Unit,
    onRemoveSampleData: () -> Unit,
    account: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRemoveSampleConfirm by remember { mutableStateOf(false) }
    val hasSamples = uiState.vehicles.any { SampleData.isSample(it.id) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { account.exportBackup(it) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let { account.importBackup(it) }
        }
    }

    LazyColumn(contentPadding = GarageDimens.subScreenContentPadding) {
        item {
            AccountSection(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        }
        item {
            SharedGarageSection(onOpenMembers = onOpenMembers, modifier = Modifier.padding(bottom = 12.dp))
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
            ClaudeAiSection(modifier = Modifier.padding(bottom = 12.dp))
        }

        item {
            GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Getting started", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The intro cards from the first launch, and the tip at the top of each screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    OutlinedButton(onClick = onShowIntro, modifier = Modifier.weight(1f)) { Text("Show intro") }
                    OutlinedButton(onClick = onResetTips, modifier = Modifier.weight(1f)) { Text("Show tips again") }
                }
                Text("Sample data", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    if (hasSamples) {
                        "Sample truck and Sample car are made-up examples. They stay on this phone: never synced, shared or backed up."
                    } else {
                        "Two made-up vehicles with a year and a half of records, to try things on. They stay on this phone: never synced, shared or backed up."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { if (hasSamples) showRemoveSampleConfirm = true else onAddSampleData() },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text(if (hasSamples) "Remove sample data" else "Add sample vehicles") }
            }
        }

        item {
            MileageReminderCard(uiState.notificationPrefs, account, modifier = Modifier.padding(bottom = 12.dp))
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
                    "Wipes all data on this device and starts over with the two sample vehicles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text("Reset to sample data", color = garageColors.alarmText) }
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Reset to sample data?",
            message = "This will erase all current data on this device and load the two sample vehicles. Export a backup first if you want to keep it.",
            confirmLabel = "Reset",
            onConfirm = { account.resetToSample() },
            onDismiss = { showResetConfirm = false },
        )
    }
    if (showRemoveSampleConfirm) {
        ConfirmDialog(
            title = "Remove sample data?",
            message = "Deletes the two sample vehicles and everything logged on them. Your own vehicles aren't touched.",
            confirmLabel = "Remove",
            onConfirm = onRemoveSampleData,
            onDismiss = { showRemoveSampleConfirm = false },
        )
    }
}

private val weekdayOrder = listOf(
    Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
)

@Composable
private fun MileageReminderCard(saved: NotificationPrefsEntity, account: AccountViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf(saved) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    GarageCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mileage reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A notification nudging you to log your current mileage, on whatever schedule you pick.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = draft.enabled,
                onCheckedChange = { checked ->
                    draft = draft.copy(enabled = checked)
                    if (checked && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                colors = garageSwitchColors(),
            )
        }

        if (draft.enabled) {
            Text("How often", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            SegmentedControl(
                options = ReminderCadence.entries.map { it.name },
                selected = draft.cadence,
                onSelect = { draft = draft.copy(cadence = it) },
            )

            when (ReminderCadence.valueOf(draft.cadence)) {
                ReminderCadence.Weekly -> {
                    Text("Which day", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    SegmentedControl(
                        options = weekdayOrder.map { dayOfWeekName(it).take(3) },
                        selected = dayOfWeekName(draft.dayOfWeek).take(3),
                        onSelect = { abbrev -> weekdayOrder.find { dayOfWeekName(it).take(3) == abbrev }?.let { draft = draft.copy(dayOfWeek = it) } },
                    )
                }
                ReminderCadence.Monthly -> {
                    LabeledTextField(
                        "Day of month (1-31)", draft.dayOfMonth.toString(),
                        { it.toIntOrNull()?.let { d -> draft = draft.copy(dayOfMonth = d.coerceIn(1, 31)) } },
                        keyboardType = KeyboardType.Number,
                    )
                    Text(
                        "Months shorter than this use their last day instead.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ReminderCadence.Yearly -> {
                    Text("Which month", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    MonthDropdown(selectedMonth = draft.month, onSelect = { draft = draft.copy(month = it) })
                    LabeledTextField(
                        "Day of month (1-31)", draft.dayOfMonth.toString(),
                        { it.toIntOrNull()?.let { d -> draft = draft.copy(dayOfMonth = d.coerceIn(1, 31)) } },
                        keyboardType = KeyboardType.Number,
                    )
                    Text(
                        "Months shorter than this use their last day instead.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ReminderCadence.Daily -> Unit
            }

            TimeField("Time", draft.hour, draft.minute) { h, m -> draft = draft.copy(hour = h, minute = m) }
        }

        Button(
            onClick = { account.saveNotificationPrefs(context, draft) },
            enabled = draft != saved,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) { Text("Save reminder settings") }
    }
}

@Composable
private fun MonthDropdown(selectedMonth: Int, onSelect: (Int) -> Unit) {
    ReadOnlyDropdownField(
        displayValue = monthName(selectedMonth),
        options = 0..11,
        optionLabel = { monthName(it) },
        onSelect = onSelect,
    )
}
