package com.garagelog.app.ui.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.todayIso
import java.util.UUID

private data class LogFormState(
    val vehicleId: String,
    val date: String,
    val mileage: String,
    val category: String,
    val task: String,
    val cost: String,
    val parts: String,
    val notes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFormSheet(
    entry: LogEntryEntity?,
    vehicles: List<VehicleEntity>,
    defaultVehicleId: String?,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onSave: (LogEntryEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var form by remember(entry?.id) {
        mutableStateOf(
            LogFormState(
                vehicleId = entry?.vehicleId ?: defaultVehicleId ?: "",
                date = entry?.date ?: todayIso(),
                mileage = entry?.mileage?.toString() ?: "",
                category = entry?.category ?: LogCategory.Routine.name,
                task = entry?.task ?: "",
                cost = entry?.cost?.toString() ?: "",
                parts = entry?.parts ?: "",
                notes = entry?.notes ?: "",
            ),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding(),
        ) {
            Text(if (entry == null) "New log entry" else "Edit log entry", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

            VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
            DateField("Date", form.date) { form = form.copy(date = it) }
            LabeledTextField("Mileage", form.mileage, { form = form.copy(mileage = it) }, keyboardType = KeyboardType.Number)

            Text("Category", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
            SegmentedControl(
                options = listOf(LogCategory.Routine.name, LogCategory.Repair.name, LogCategory.Upgrade.name, LogCategory.Diagnostic.name),
                selected = form.category,
                onSelect = { form = form.copy(category = it) },
            )

            LabeledTextField("Task / service", form.task, { form = form.copy(task = it) })
            LabeledTextField("Cost (\$)", form.cost, { form = form.copy(cost = it) }, keyboardType = KeyboardType.Decimal)
            LabeledTextField("Parts used", form.parts, { form = form.copy(parts = it) })
            LabeledTextField("Notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 2)

            if (entry != null) {
                PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.LOG, ownerId = entry.id)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp)) {
                if (entry != null) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                }
                androidx.compose.material3.Button(
                    onClick = {
                        onSave(
                            LogEntryEntity(
                                id = entry?.id ?: UUID.randomUUID().toString(),
                                vehicleId = form.vehicleId,
                                date = form.date.ifBlank { todayIso() },
                                mileage = form.mileage.trim().toIntOrNull(),
                                category = form.category,
                                task = form.task.trim().ifBlank { "Untitled entry" },
                                cost = form.cost.trim().toDoubleOrNull(),
                                parts = form.parts.trim(),
                                notes = form.notes.trim(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }

    if (showDeleteConfirm && entry != null) {
        ConfirmDialog(
            title = "Delete log entry?",
            message = "This can't be undone.",
            onConfirm = { onDelete(entry.id) },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
