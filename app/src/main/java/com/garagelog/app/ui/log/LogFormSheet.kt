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
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.ReadOnlyDropdownField
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
    val fulfillsScheduleId: String?,
)

@Composable
fun LogFormSheet(
    entry: LogEntryEntity?,
    vehicles: List<VehicleEntity>,
    schedules: List<MaintenanceScheduleEntity>,
    defaultVehicleId: String?,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onSave: (LogEntryEntity) -> Unit,
    onDelete: (String) -> Unit,
    // Seeds a brand-new entry's fields (e.g. from "mark maintenance done") without treating it as
    // an existing, editable/deletable entry the way a non-null `entry` would.
    prefill: LogEntryEntity? = null,
) {
    // Generated once, up front, rather than at Save time — photos need a real id to attach to
    // (see PhotoGridSection below), and a brand-new entry has no id until it's first saved. Using
    // this same id both for photos-added-before-save and as the entity's final id at Save time
    // means they end up pointing at the same row instead of the photos being orphaned.
    val stableId = remember(entry?.id) { entry?.id ?: UUID.randomUUID().toString() }

    var form by remember(entry?.id) {
        mutableStateOf(
            LogFormState(
                vehicleId = entry?.vehicleId ?: prefill?.vehicleId ?: defaultVehicleId ?: "",
                date = entry?.date ?: prefill?.date ?: todayIso(),
                mileage = entry?.mileage?.toString() ?: prefill?.mileage?.toString() ?: "",
                category = entry?.category ?: prefill?.category ?: LogCategory.Routine.name,
                task = entry?.task ?: prefill?.task ?: "",
                cost = entry?.cost?.toString() ?: prefill?.cost?.toString() ?: "",
                parts = entry?.parts ?: prefill?.parts ?: "",
                notes = entry?.notes ?: prefill?.notes ?: "",
                fulfillsScheduleId = entry?.fulfillsScheduleId ?: prefill?.fulfillsScheduleId,
            ),
        )
    }

    FormSheetScaffold(
        title = if (entry == null) "New log entry" else "Edit log entry",
        onDismiss = {
            // A new entry's photos were written straight to the DB the moment they were picked
            // (see PhotoGridSection), since attaching a photo needs a real owner id right away —
            // if the entry itself is never saved, clean those up rather than leaving them orphaned.
            if (entry == null) viewModel.discardPhotosForOwner(PhotoOwnerType.LOG, stableId)
            onDismiss()
        },
        showDelete = entry != null,
        deleteTitle = "Delete log entry?",
        onDelete = { entry?.let { onDelete(it.id) } },
        onSave = {
            onSave(
                LogEntryEntity(
                    id = stableId,
                    vehicleId = form.vehicleId,
                    date = form.date.ifBlank { todayIso() },
                    mileage = form.mileage.trim().toIntOrNull(),
                    category = form.category,
                    task = form.task.trim().ifBlank { "Untitled entry" },
                    cost = form.cost.trim().toDoubleOrNull(),
                    parts = form.parts.trim(),
                    notes = form.notes.trim(),
                    fulfillsScheduleId = form.fulfillsScheduleId,
                ),
            )
        },
    ) {
        VehicleDropdown("Vehicle", vehicles, form.vehicleId) {
            // A schedule link only makes sense for the vehicle it was picked under.
            form = form.copy(vehicleId = it, fulfillsScheduleId = null)
        }
        DateField("Date", form.date) { form = form.copy(date = it) }
        LabeledTextField("Mileage", form.mileage, { form = form.copy(mileage = it) }, keyboardType = KeyboardType.Number)

        Text("Category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
        SegmentedControl(
            options = listOf(LogCategory.Routine.name, LogCategory.Repair.name, LogCategory.Upgrade.name, LogCategory.Diagnostic.name),
            selected = form.category,
            onSelect = { form = form.copy(category = it, fulfillsScheduleId = if (it == LogCategory.Routine.name) form.fulfillsScheduleId else null) },
        )

        LabeledTextField("Task / service", form.task, { form = form.copy(task = it) })

        if (form.category == LogCategory.Routine.name) {
            val vehicleSchedules = schedules.filter { it.vehicleId == form.vehicleId }
            val options: List<MaintenanceScheduleEntity?> = listOf(null) + vehicleSchedules
            ReadOnlyDropdownField(
                displayValue = vehicleSchedules.find { it.id == form.fulfillsScheduleId }?.taskName ?: "None",
                options = options,
                optionLabel = { it?.taskName ?: "None" },
                onSelect = { form = form.copy(fulfillsScheduleId = it?.id) },
                label = "Fulfills schedule",
            )
        }

        LabeledTextField("Cost (\$)", form.cost, { form = form.copy(cost = it) }, keyboardType = KeyboardType.Decimal)
        LabeledTextField("Parts used", form.parts, { form = form.copy(parts = it) })
        LabeledTextField("Notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 2)

        PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.LOG, ownerId = stableId)
    }
}
