package com.garagelog.app.ui.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.RecordsViewModel
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.MileageField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.ReadOnlyDropdownField
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.UserStamp
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.ui.theme.garageSwitchColors
import com.garagelog.app.util.formatDate
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
    val missedFillUp: Boolean,
    val inProgress: Boolean,
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
    records: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory),
    onDismiss: () -> Unit,
    onSave: (LogEntryEntity) -> Unit,
    onDelete: (String) -> Unit,
    /** See FormSheetScaffold; null = editable. */
    lockedReason: String? = null,
    canDelete: Boolean = true,
    // Seeds a brand-new entry's fields (e.g. from "mark maintenance done") without treating it as
    // an existing, editable/deletable entry the way a non-null `entry` would.
    prefill: LogEntryEntity? = null,
    // Who added it ("E.Forr"), shown at the top; null in a solo garage or for a new entry.
    stamp: String? = null,
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
                // Most fill-ups fill the tank: a new one starts on, an existing one keeps what it saved.
                fullTank = entry?.fullTank ?: true,
                missedFillUp = source?.missedFillUp ?: false,
                inProgress = source?.inProgress ?: false,
            ),
        )
    }

    val vehicleSchedules = schedules.filter { it.vehicleId == form.vehicleId }
    val linkedSchedule = vehicleSchedules.find { it.id == form.fulfillsScheduleId }

    // A fill-up's odometer reading is what every MPG calculation is built on, and unlike other
    // categories there's no way to infer it later — so it's the one field the form won't save
    // without. Other categories stay optional.
    val mileageRequired = form.category == LogCategory.Fuel.name
    val hasMileage = form.mileage.trim().toIntOrNull() != null

    FormSheetScaffold(
        title = if (entry == null) "New log entry" else "Edit log entry",
        onDismiss = {
            // A new entry's photos were written straight to the DB the moment they were picked
            // (see PhotoGridSection), since attaching a photo needs a real owner id right away —
            // if the entry itself is never saved, clean those up rather than leaving them orphaned.
            if (entry == null) records.discardPhotosForOwner(PhotoOwnerType.LOG, stableId)
            onDismiss()
        },
        showDelete = entry != null && canDelete,
        lockedReason = lockedReason,
        saveEnabled = !mileageRequired || hasMileage,
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
                    missedFillUp = form.category == LogCategory.Fuel.name && form.missedFillUp,
                    // Fill-ups and odometer readings happen in one go; only a job can be left unfinished.
                    inProgress = !isQuickCategory(form.category) && form.inProgress,
                ),
            )
        },
    ) {
        stamp?.takeIf { it.isNotBlank() }?.let { UserStamp("Added by $it", modifier = Modifier.padding(bottom = 6.dp)) }

        // Finishing a job that was saved as in progress: the date and mileage become the day it was
        // done (today and the vehicle's current miles to start, both editable), and saving it resets
        // a linked maintenance item.
        val finish = {
            val miles = vehicles.find { it.id == form.vehicleId }?.miles
            form = form.copy(inProgress = false, date = todayIso(), mileage = miles?.toString() ?: form.mileage)
        }
        if (entry?.inProgress == true && form.inProgress) {
            GarageCard(accentColor = garageColors.warn, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("In progress since ${formatDate(entry.date)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Done with it? Finish sets the date and mileage to today's; check them, then Save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = finish, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Finish job") }
            }
        }

        VehicleDropdown("Vehicle", vehicles, form.vehicleId) {
            // A schedule link only makes sense for the vehicle it was picked under.
            form = form.copy(vehicleId = it, fulfillsScheduleId = null)
        }
        DateField("Date", form.date) { form = form.copy(date = it) }
        MileageField(
            if (mileageRequired) "Mileage (required)" else "Mileage",
            form.mileage,
            { form = form.copy(mileage = it) },
        )

        Text("Category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
        SegmentedControl(
            options = LogCategory.entries.map { it.name },
            selected = form.category,
            onSelect = { form = form.copy(category = it, fulfillsScheduleId = if (it == LogCategory.Routine.name) form.fulfillsScheduleId else null) },
        )

        when (form.category) {
            LogCategory.Fuel.name -> {
                // Cost first, then gallons: the order a pump's display reads.
                LabeledTextField("Total cost (\$)", form.cost, { form = form.copy(cost = it) }, keyboardType = KeyboardType.Decimal)
                LabeledTextField("Gallons", form.gallons, { form = form.copy(gallons = it) }, keyboardType = KeyboardType.Decimal)
                FuelSwitch(
                    label = "Full tank",
                    note = "Off for a partial fill. MPG is measured between full tanks.",
                    checked = form.fullTank,
                    onCheckedChange = { form = form.copy(fullTank = it) },
                )
                FuelSwitch(
                    label = "Skipped logging a fill-up before this one",
                    note = "The missed fuel is unknown, so MPG starts counting again from here.",
                    checked = form.missedFillUp,
                    onCheckedChange = { form = form.copy(missedFillUp = it) },
                )
            }
            LogCategory.Mileage.name -> Unit
            else -> {
                FuelSwitch(
                    label = "In progress",
                    note = "Started, not finished (parts bought, say). The cost counts now; a linked maintenance item resets when you finish it.",
                    checked = form.inProgress,
                    onCheckedChange = { on -> if (!on && entry?.inProgress == true) finish() else form = form.copy(inProgress = on) },
                )
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
            PhotoGridSection(records = records, ownerType = PhotoOwnerType.LOG, ownerId = stableId)
        }
    }
}


@Composable
private fun FuelSwitch(label: String, note: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = garageSwitchColors())
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
