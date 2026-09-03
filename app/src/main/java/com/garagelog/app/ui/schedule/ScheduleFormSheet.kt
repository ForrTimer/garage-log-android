package com.garagelog.app.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.components.capitalizeWords
import java.util.UUID

private data class ScheduleFormState(
    val vehicleId: String,
    val taskName: String,
    val intervalMiles: String,
    val intervalMonths: String,
    val lastDoneMileage: String,
    val lastDoneDate: String,
)

@Composable
fun ScheduleFormSheet(
    schedule: MaintenanceScheduleEntity?,
    vehicles: List<VehicleEntity>,
    defaultVehicleId: String?,
    onDismiss: () -> Unit,
    onSave: (MaintenanceScheduleEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var form by remember(schedule?.id) {
        mutableStateOf(
            ScheduleFormState(
                vehicleId = schedule?.vehicleId ?: defaultVehicleId ?: "",
                taskName = schedule?.taskName ?: "",
                intervalMiles = schedule?.intervalMiles?.toString() ?: "",
                intervalMonths = schedule?.intervalMonths?.toString() ?: "",
                lastDoneMileage = schedule?.lastDoneMileage?.toString() ?: "",
                lastDoneDate = schedule?.lastDoneDate ?: "",
            ),
        )
    }

    FormSheetScaffold(
        title = if (schedule == null) "New maintenance interval" else "Edit maintenance interval",
        onDismiss = onDismiss,
        showDelete = schedule != null,
        deleteTitle = "Delete maintenance interval?",
        onDelete = { schedule?.let { onDelete(it.id) } },
        onSave = {
            onSave(
                MaintenanceScheduleEntity(
                    id = schedule?.id ?: UUID.randomUUID().toString(),
                    vehicleId = form.vehicleId,
                    taskName = form.taskName.trim().ifBlank { "Untitled schedule" },
                    intervalMiles = form.intervalMiles.trim().toIntOrNull(),
                    intervalMonths = form.intervalMonths.trim().toIntOrNull(),
                    lastDoneMileage = form.lastDoneMileage.trim().toIntOrNull(),
                    lastDoneDate = form.lastDoneDate.ifBlank { null },
                ),
            )
        },
    ) {
        VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
        LabeledTextField(
            "Task",
            form.taskName,
            { form = form.copy(taskName = capitalizeWords(it)) },
            capitalization = KeyboardCapitalization.Words,
        )
        LabeledTextField("Interval (miles)", form.intervalMiles, { form = form.copy(intervalMiles = it) }, keyboardType = KeyboardType.Number)
        LabeledTextField("Interval (months)", form.intervalMonths, { form = form.copy(intervalMonths = it) }, keyboardType = KeyboardType.Number)
        LabeledTextField("Last done — mileage", form.lastDoneMileage, { form = form.copy(lastDoneMileage = it) }, keyboardType = KeyboardType.Number)
        DateField("Last done — date", form.lastDoneDate) { form = form.copy(lastDoneDate = it) }
    }
}
