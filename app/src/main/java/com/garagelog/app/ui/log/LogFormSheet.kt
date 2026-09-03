package com.garagelog.app.ui.log

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.VehicleDropdown
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

    FormSheetScaffold(
        title = if (entry == null) "New log entry" else "Edit log entry",
        onDismiss = onDismiss,
        showDelete = entry != null,
        deleteTitle = "Delete log entry?",
        onDelete = { entry?.let { onDelete(it.id) } },
        onSave = {
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
    ) {
        VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
        DateField("Date", form.date) { form = form.copy(date = it) }
        LabeledTextField("Mileage", form.mileage, { form = form.copy(mileage = it) }, keyboardType = KeyboardType.Number)

        Text("Category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
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
    }
}
