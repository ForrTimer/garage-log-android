package com.garagelog.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.CommonMaintenanceServices
import com.garagelog.app.util.todayIso
import java.io.File
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
    val severeDustyAreas: Boolean,
    val severeTowing: Boolean,
    val severeExtendedIdling: Boolean,
    val severeLowSpeedColdWeather: Boolean,
    val severeHeavyCityTrafficHot: Boolean,
    val severeMountainousHot: Boolean,
    val severeFrequentTowing: Boolean,
    val severeDeepWater: Boolean,
)

@Composable
fun VehicleFormSheet(
    vehicle: VehicleEntity?,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onSave: (VehicleEntity, List<String>) -> Unit,
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
                severeDustyAreas = vehicle?.severeDustyAreas ?: false,
                severeTowing = vehicle?.severeTowing ?: false,
                severeExtendedIdling = vehicle?.severeExtendedIdling ?: false,
                severeLowSpeedColdWeather = vehicle?.severeLowSpeedColdWeather ?: false,
                severeHeavyCityTrafficHot = vehicle?.severeHeavyCityTrafficHot ?: false,
                severeMountainousHot = vehicle?.severeMountainousHot ?: false,
                severeFrequentTowing = vehicle?.severeFrequentTowing ?: false,
                severeDeepWater = vehicle?.severeDeepWater ?: false,
            ),
        )
    }
    var selectedServices by remember(vehicle?.id) {
        mutableStateOf(if (vehicle == null) CommonMaintenanceServices.defaultSelected else emptySet())
    }

    FormSheetScaffold(
        title = if (vehicle == null) "Add vehicle" else "Edit vehicle",
        onDismiss = onDismiss,
        showDelete = vehicle != null,
        deleteTitle = "Delete vehicle?",
        deleteMessage = "This will also delete all of its logs, issues, build phases, and maintenance schedules. This can't be undone.",
        onDelete = { vehicle?.let { onDelete(it.id) } },
        onSave = {
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
                    severeDustyAreas = form.severeDustyAreas,
                    severeTowing = form.severeTowing,
                    severeExtendedIdling = form.severeExtendedIdling,
                    severeLowSpeedColdWeather = form.severeLowSpeedColdWeather,
                    severeHeavyCityTrafficHot = form.severeHeavyCityTrafficHot,
                    severeMountainousHot = form.severeMountainousHot,
                    severeFrequentTowing = form.severeFrequentTowing,
                    severeDeepWater = form.severeDeepWater,
                ),
                selectedServices.toList(),
            )
        },
    ) {
        if (vehicle != null) {
            VehiclePhotoPicker(vehicle = vehicle, viewModel = viewModel)
        }

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

        Text("Severe-duty conditions", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Any of these checked halves computed maintenance intervals, per typical OEM severe-duty schedules.",
            style = MaterialTheme.typography.bodySmall,
            color = garageColors.textMuted,
        )
        CheckboxRow("Driving in dusty areas", form.severeDustyAreas) { form = form.copy(severeDustyAreas = it) }
        CheckboxRow("Towing a trailer", form.severeTowing) { form = form.copy(severeTowing = it) }
        CheckboxRow("Idling for extended periods", form.severeExtendedIdling) { form = form.copy(severeExtendedIdling = it) }
        CheckboxRow("Low speed / short trips in below-freezing temps", form.severeLowSpeedColdWeather) { form = form.copy(severeLowSpeedColdWeather = it) }
        CheckboxRow("Heavy city traffic above 90°F", form.severeHeavyCityTrafficHot) { form = form.copy(severeHeavyCityTrafficHot = it) }
        CheckboxRow("Hilly/mountainous terrain above 90°F", form.severeMountainousHot) { form = form.copy(severeMountainousHot = it) }
        CheckboxRow("Frequent trailer towing", form.severeFrequentTowing) { form = form.copy(severeFrequentTowing = it) }
        CheckboxRow("Driven through deep water", form.severeDeepWater) { form = form.copy(severeDeepWater = it) }

        if (vehicle == null) {
            Text("Starter maintenance schedule", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Check the services you want tracked — default intervals get added, which you can edit any time.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
            )
            CommonMaintenanceServices.all.forEach { template ->
                CheckboxRow(template.name, template.name in selectedServices) { checked ->
                    selectedServices = if (checked) selectedServices + template.name else selectedServices - template.name
                }
            }
        }
    }
}

@Composable
private fun VehiclePhotoPicker(vehicle: VehicleEntity, viewModel: GarageLogViewModel) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.setVehiclePhoto(vehicle, uri)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            if (vehicle.photoPath != null) {
                AsyncImage(
                    model = File(vehicle.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                if (vehicle.photoPath == null) "Add a photo" else "Change photo",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            if (vehicle.photoPath != null) {
                Text(
                    "Remove photo",
                    color = garageColors.alarmText,
                    style = MaterialTheme.typography.labelMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).clickable { viewModel.removeVehiclePhoto(vehicle) },
                )
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
