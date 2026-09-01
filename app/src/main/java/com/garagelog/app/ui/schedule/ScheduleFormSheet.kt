package com.garagelog.app.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageColors
import java.util.UUID

private data class ScheduleFormState(
    val vehicleId: String,
    val taskName: String,
    val intervalMiles: String,
    val intervalMonths: String,
    val lastDoneMileage: String,
    val lastDoneDate: String,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding(),
        ) {
            Text(if (schedule == null) "New maintenance interval" else "Edit maintenance interval", style = MaterialTheme.typography.titleLarge)

            VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
            LabeledTextField("Task", form.taskName, { form = form.copy(taskName = it) })
            LabeledTextField("Interval (miles)", form.intervalMiles, { form = form.copy(intervalMiles = it) }, keyboardType = KeyboardType.Number)
            LabeledTextField("Interval (months)", form.intervalMonths, { form = form.copy(intervalMonths = it) }, keyboardType = KeyboardType.Number)
            LabeledTextField("Last done — mileage", form.lastDoneMileage, { form = form.copy(lastDoneMileage = it) }, keyboardType = KeyboardType.Number)
            DateField("Last done — date", form.lastDoneDate) { form = form.copy(lastDoneDate = it) }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp)) {
                if (schedule != null) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {
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
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }

    if (showDeleteConfirm && schedule != null) {
        ConfirmDialog(
            title = "Delete maintenance interval?",
            message = "This can't be undone.",
            onConfirm = { onDelete(schedule.id) },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
