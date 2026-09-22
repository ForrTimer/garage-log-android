package com.garagelog.app.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.ai.scheduleKey
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.theme.garageColors

/**
 * Bob's proposed schedule for a vehicle that already exists, for review before anything is added.
 * Services the vehicle already tracks are shown greyed out rather than offered twice.
 */
@Composable
fun BobScheduleSheet(
    vehicle: VehicleEntity,
    existing: List<MaintenanceScheduleEntity>,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onAdd: (List<MaintenanceScheduleEntity>) -> Unit,
) {
    var drafts by remember(vehicle.id) { mutableStateOf(emptyList<ServiceDraft>()) }
    val tracked = remember(existing) { existing.map { it.taskName.trim().lowercase() }.toSet() }
    val selected = drafts.filter { it.checked && it.name.lowercase() !in tracked }

    FormSheetScaffold(
        title = "Schedule for ${vehicle.name}",
        onDismiss = onDismiss,
        onSave = {
            val now = System.currentTimeMillis()
            onAdd(selected.map { it.toSchedule(vehicle.id, now) })
        },
        saveEnabled = selected.isNotEmpty(),
        showDelete = false,
        deleteTitle = "",
        onDelete = {},
    ) {
        Text(
            "$ASSISTANT_NAME looks up this vehicle's own maintenance schedule. Nothing is added until you tick it and save.",
            style = MaterialTheme.typography.bodySmall,
            color = garageColors.textMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        BobScheduleButton(
            viewModel = viewModel,
            runKey = scheduleKey(vehicle.id),
            ready = vehicle.year != null && vehicle.make.isNotBlank() && vehicle.model.isNotBlank(),
            onRun = {
                viewModel.suggestSchedule(vehicle, alreadyTracked = existing.map { it.taskName }) { suggestions ->
                    drafts = suggestions.map {
                        ServiceDraft(
                            name = it.name,
                            miles = it.intervalMiles?.toString().orEmpty(),
                            months = it.intervalMonths?.toString().orEmpty(),
                            checked = true,
                            note = it.note.ifBlank { null },
                        )
                    }
                }
            },
        )
        ServiceChecklist(drafts, onChange = { drafts = it }, alreadyTracked = tracked)
    }
}

/** Shared by the Add vehicle form and the Maintenance screen's suggestion sheet. */
@Composable
fun BobScheduleButton(viewModel: GarageLogViewModel, runKey: String, ready: Boolean, onRun: () -> Unit) {
    val hasKey by viewModel.hasAiKey.collectAsState()
    val runs by viewModel.aiRuns.collectAsState()
    val run = runs[runKey]
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        when {
            run?.running == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(run.statusText, style = MaterialTheme.typography.bodyMedium)
            }
            !hasKey -> Text(
                "Add an API key in Settings → $ASSISTANT_NAME to have $ASSISTANT_NAME fill this in.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
            )
            !ready -> Text(
                "Enter the year, make and model to have $ASSISTANT_NAME look up its schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
            )
            else -> {
                ActionLink(if (run?.error != null) "Try again with $ASSISTANT_NAME" else "Fill in with $ASSISTANT_NAME", onClick = onRun)
                run?.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = garageColors.alarmText, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

