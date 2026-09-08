package com.garagelog.app.ui.log

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.ReadOnlyDropdownField
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageSwitchColors
import com.garagelog.app.util.todayIso
import java.util.UUID

private data class LogFormState(
    val vehicleId: String,
    val date: String,
    val mileage: String,
    val category: String,
    val task: String,
    val cost: String,
    val parts: List<String>,
    val notes: String,
    val fulfillsScheduleId: String?,
    val gallons: String,
    val fullTank: Boolean,
)

/** Categories light enough that a photo grid/parts list would just be clutter, not a real need. */
private fun isQuickCategory(category: String) = category == LogCategory.Fuel.name || category == LogCategory.Mileage.name

private fun splitParts(raw: String): List<String> {
    val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return parts.ifEmpty { listOf("") }
}

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
        val source = entry ?: prefill
        mutableStateOf(
            LogFormState(
                vehicleId = source?.vehicleId ?: defaultVehicleId ?: "",
                date = source?.date ?: todayIso(),
                mileage = source?.mileage?.toString() ?: "",
                category = source?.category ?: LogCategory.Routine.name,
                task = source?.task ?: "",
                cost = source?.cost?.toString() ?: "",
                parts = splitParts(source?.parts ?: ""),
                notes = source?.notes ?: "",
                fulfillsScheduleId = source?.fulfillsScheduleId,
                gallons = source?.gallons?.toString() ?: "",
                fullTank = source?.fullTank ?: false,
            ),
        )
    }

    val vehicleSchedules = schedules.filter { it.vehicleId == form.vehicleId }
    val linkedSchedule = vehicleSchedules.find { it.id == form.fulfillsScheduleId }

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
            val task = when {
                form.category == LogCategory.Fuel.name -> "Fuel"
                form.category == LogCategory.Mileage.name -> "Mileage update"
                form.category == LogCategory.Routine.name && linkedSchedule != null -> linkedSchedule.taskName
                else -> form.task.trim().ifBlank { "Untitled entry" }
            }
            onSave(
                LogEntryEntity(
                    id = stableId,
                    vehicleId = form.vehicleId,
                    date = form.date.ifBlank { todayIso() },
                    mileage = form.mileage.trim().toIntOrNull(),
                    category = form.category,
                    task = task,
                    cost = if (isQuickCategory(form.category) && form.category == LogCategory.Mileage.name) null else form.cost.trim().toDoubleOrNull(),
                    parts = if (isQuickCategory(form.category)) "" else form.parts.filter { it.isNotBlank() }.joinToString(", "),
                    notes = form.notes.trim(),
                    fulfillsScheduleId = if (form.category == LogCategory.Routine.name) form.fulfillsScheduleId else null,
                    gallons = if (form.category == LogCategory.Fuel.name) form.gallons.trim().toDoubleOrNull() else null,
                    fullTank = form.category == LogCategory.Fuel.name && form.fullTank,
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
            options = LogCategory.entries.map { it.name },
            selected = form.category,
            onSelect = { form = form.copy(category = it, fulfillsScheduleId = if (it == LogCategory.Routine.name) form.fulfillsScheduleId else null) },
        )

        when (form.category) {
            LogCategory.Fuel.name -> {
                LabeledTextField("Gallons", form.gallons, { form = form.copy(gallons = it) }, keyboardType = KeyboardType.Decimal)
                LabeledTextField("Total cost (\$)", form.cost, { form = form.copy(cost = it) }, keyboardType = KeyboardType.Decimal)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Switch(checked = form.fullTank, onCheckedChange = { form = form.copy(fullTank = it) }, colors = garageSwitchColors())
                    Text("Full tank", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                }
            }
            LogCategory.Mileage.name -> Unit
            else -> {
                if (form.category == LogCategory.Routine.name) {
                    val options: List<MaintenanceScheduleEntity?> = listOf(null) + vehicleSchedules
                    ReadOnlyDropdownField(
                        displayValue = linkedSchedule?.taskName ?: "None",
                        options = options,
                        optionLabel = { it?.taskName ?: "None" },
                        onSelect = { form = form.copy(fulfillsScheduleId = it?.id) },
                        label = "Fulfills schedule",
                    )
                }
                if (form.category != LogCategory.Routine.name || linkedSchedule == null) {
                    LabeledTextField("Task / service", form.task, { form = form.copy(task = it) })
                }
                LabeledTextField("Cost (\$)", form.cost, { form = form.copy(cost = it) }, keyboardType = KeyboardType.Decimal)

                Text("Parts used", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
                form.parts.forEachIndexed { index, part ->
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                        LabeledTextField(
                            label = "Part ${index + 1}",
                            value = part,
                            onValueChange = { value -> form = form.copy(parts = form.parts.toMutableList().also { it[index] = value }) },
                            modifier = Modifier.weight(1f),
                        )
                        if (form.parts.size > 1) {
                            IconButton(onClick = { form = form.copy(parts = form.parts.toMutableList().also { it.removeAt(index) }) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove part")
                            }
                        }
                    }
                }
                ActionLink("+ Add part", onClick = { form = form.copy(parts = form.parts + "") }, modifier = Modifier.padding(top = 6.dp))
            }
        }

        LabeledTextField("Notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 2)

        if (!isQuickCategory(form.category)) {
            PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.LOG, ownerId = stableId)
        }
    }
}

