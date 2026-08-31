package com.garagelog.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.todayIso
import java.util.UUID

private data class VehicleFormState(
    val name: String,
    val year: String,
    val make: String,
    val model: String,
    val engine: String,
    val drivetrain: String,
    val vin: String,
    val color: String,
    val miles: String,
    val role: String,
    val notes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormSheet(
    vehicle: VehicleEntity?,
    onDismiss: () -> Unit,
    onSave: (VehicleEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var form by remember(vehicle?.id) {
        mutableStateOf(
            VehicleFormState(
                name = vehicle?.name ?: "",
                year = vehicle?.year?.toString() ?: "",
                make = vehicle?.make ?: "",
                model = vehicle?.model ?: "",
                engine = vehicle?.engine ?: "",
                drivetrain = vehicle?.drivetrain ?: "",
                vin = vehicle?.vin ?: "",
                color = vehicle?.color ?: "",
                miles = vehicle?.miles?.toString() ?: "",
                role = vehicle?.role ?: "",
                notes = vehicle?.notes ?: "",
            ),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding()) {
            Text(if (vehicle == null) "Add vehicle" else "Edit vehicle", style = MaterialTheme.typography.titleLarge)

            LabeledTextField("Name / nickname", form.name, { form = form.copy(name = it) })
            LabeledTextField("Year", form.year, { form = form.copy(year = it) }, keyboardType = KeyboardType.Number)
            LabeledTextField("Make", form.make, { form = form.copy(make = it) })
            LabeledTextField("Model", form.model, { form = form.copy(model = it) })
            LabeledTextField("Engine", form.engine, { form = form.copy(engine = it) })
            LabeledTextField("Drivetrain", form.drivetrain, { form = form.copy(drivetrain = it) })
            LabeledTextField("VIN", form.vin, { form = form.copy(vin = it) })
            LabeledTextField("Color", form.color, { form = form.copy(color = it) })
            LabeledTextField("Current mileage", form.miles, { form = form.copy(miles = it) }, keyboardType = KeyboardType.Number)
            LabeledTextField("Role / notes", form.role, { form = form.copy(role = it) }, singleLine = false, minLines = 2)
            LabeledTextField("Free-form notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 2)

            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp)) {
                if (vehicle != null) {
                    OutlinedButton(onClick = { onDelete(vehicle.id) }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {
                        onSave(
                            VehicleEntity(
                                id = vehicle?.id ?: UUID.randomUUID().toString(),
                                name = form.name.trim().ifBlank { "Unnamed vehicle" },
                                year = form.year.trim().toIntOrNull(),
                                make = form.make.trim(),
                                model = form.model.trim(),
                                engine = form.engine.trim(),
                                drivetrain = form.drivetrain.trim(),
                                vin = form.vin.trim(),
                                color = form.color.trim(),
                                miles = form.miles.trim().toIntOrNull(),
                                milesDate = todayIso(),
                                role = form.role.trim(),
                                notes = form.notes.trim(),
                                sortOrder = vehicle?.sortOrder ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}
