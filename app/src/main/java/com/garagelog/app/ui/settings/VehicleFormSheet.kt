package com.garagelog.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.ai.scheduleKey
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.RecordsViewModel
import com.garagelog.app.ui.ai.BobViewModel
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.MileageField
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.SuggestField
import com.garagelog.app.ui.schedule.BobScheduleButton
import com.garagelog.app.ui.schedule.ServiceChecklist
import com.garagelog.app.ui.schedule.ServiceDraft
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.CommonMaintenanceServices
import com.garagelog.app.util.todayIso
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun VehicleFormSheet(
    vehicle: VehicleEntity?,
    records: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory),
    bob: BobViewModel = viewModel(factory = BobViewModel.Factory),
    onDismiss: () -> Unit,
    onSave: (VehicleEntity, List<MaintenanceScheduleEntity>) -> Unit,
    onDelete: (String) -> Unit,
    /** See FormSheetScaffold; null = editable. */
    lockedReason: String? = null,
    canDelete: Boolean = true,
) {
    // A freshly-picked photo is copied into app storage the instant it's picked (see
    // onPhotoPicked below) rather than saved to the DB right away — writing straight to the DB
    // used to race with Save reconstructing the VehicleEntity from scratch (which never carried
    // photoPath forward), silently wiping the photo the moment the sheet was saved. But the copy
    // itself can't wait for Save either: the picker's content:// Uri only grants short-lived read
    // access, so holding onto just the Uri until Save was tapped made the photo go unreadable
    // within seconds. Copying immediately and holding the resulting stable file path instead
    // avoids both problems. rememberSaveable (not remember) because launching the system photo
    // picker backgrounds this activity, and some devices reclaim enough of it in the meantime
    // that plain `remember` state doesn't survive the round trip back.
    var pendingPhotoPath by rememberSaveable(vehicle?.id) { mutableStateOf<String?>(null) }
    var currentPhotoPath by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.photoPath) }
    // Minted up front so an unsaved vehicle already has the id Bob's run is tracked under.
    val stableId = remember(vehicle?.id) { vehicle?.id ?: UUID.randomUUID().toString() }
    var form by remember(vehicle?.id) { mutableStateOf(VehicleFormState.from(vehicle)) }
    var services by remember(vehicle?.id) {
        mutableStateOf(
            CommonMaintenanceServices.all.map { ServiceDraft.from(it, it.name in CommonMaintenanceServices.defaultSelected) },
        )
    }
    var showMoreDetails by remember(vehicle?.id) {
        mutableStateOf(listOf(vehicle?.color, vehicle?.notes).any { !it.isNullOrBlank() })
    }
    var showSevereDuty by remember(vehicle?.id) { mutableStateOf(vehicle?.isSevereDuty ?: false) }

    fun buildEntity() = VehicleEntity(
        id = stableId,
        name = form.name.trim().ifBlank {
            listOf(form.year, form.make, form.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unnamed vehicle" }
        },
        year = form.year.trim().toIntOrNull(),
        make = form.make.trim(),
        model = form.model.trim(),
        engine = form.engine.trim(),
        drivetrain = form.drivetrain.trim(),
        vin = form.vin.trim().uppercase(),
        color = form.color.trim(),
        miles = form.miles.filter(Char::isDigit).toIntOrNull(),
        milesDate = if (form.miles.filter(Char::isDigit).toIntOrNull() != vehicle?.miles) todayIso() else vehicle?.milesDate ?: todayIso(),
        role = form.role.trim(),
        notes = form.notes.trim(),
        sortOrder = vehicle?.sortOrder ?: 0,
        photoPath = pendingPhotoPath ?: currentPhotoPath,
        severeDustyAreas = form.severeDustyAreas,
        severeTowing = form.severeTowing,
        severeExtendedIdling = form.severeExtendedIdling,
        severeLowSpeedColdWeather = form.severeLowSpeedColdWeather,
        severeHeavyCityTrafficHot = form.severeHeavyCityTrafficHot,
        severeMountainousHot = form.severeMountainousHot,
        severeFrequentTowing = form.severeFrequentTowing,
        severeDeepWater = form.severeDeepWater,
        trim = form.trim.trim(),
        bodyStyle = form.bodyStyle.trim(),
        cabStyle = if (form.isPickup) form.cabStyle.trim() else "",
        bedLength = if (form.isPickup) form.bedLength.trim() else "",
        transmissionType = form.transmissionType.trim(),
        transmissionSpeeds = if (form.transmissionType == "CVT") null else form.transmissionSpeeds.toIntOrNull(),
        cylinders = if (form.isElectric) null else form.cylinders.toIntOrNull(),
        displacementL = if (form.isElectric) null else form.displacementL.replace(',', '.').toDoubleOrNull(),
        fuelType = form.fuelType.trim(),
        aspiration = if (form.isElectric) "" else form.aspiration.trim(),
    )

    FormSheetScaffold(
        title = if (vehicle == null) "Add vehicle" else "Edit vehicle",
        onDismiss = onDismiss,
        showDelete = vehicle != null && canDelete,
        lockedReason = lockedReason,
        deleteTitle = "Delete vehicle?",
        deleteMessage = "This will also delete all of its logs, issues, and maintenance schedules. This can't be undone.",
        onDelete = { vehicle?.let { onDelete(it.id) } },
        onSave = {
            // A pending photo replacing an already-saved one leaves the old file orphaned on
            // disk once this commits — clean it up now that it's actually being superseded.
            if (pendingPhotoPath != null && currentPhotoPath != null && pendingPhotoPath != currentPhotoPath) {
                records.deletePhotoFile(currentPhotoPath!!)
            }
            val entity = buildEntity()
            val now = System.currentTimeMillis()
            val schedules = if (vehicle == null) services.filter { it.checked }.map { it.toSchedule(entity.id, now) } else emptyList()
            onSave(entity, schedules)
        },
    ) {
        VehiclePhotoPicker(
            photoPath = pendingPhotoPath ?: currentPhotoPath,
            onPhotoPicked = { uri ->
                records.copyPhotoToAppStorage(uri) { copied -> if (copied != null) pendingPhotoPath = copied }
            },
            onRemovePhoto = {
                pendingPhotoPath?.let { records.deletePhotoFile(it) }
                pendingPhotoPath = null
                if (currentPhotoPath != null) vehicle?.let { records.removeVehiclePhoto(it) }
                currentPhotoPath = null
            },
        )

        LabeledTextField(
            "Name / nickname",
            form.name,
            { form = form.copy(name = it) },
            capitalization = KeyboardCapitalization.Words,
        )

        VinLookup(records.vehicleCatalog, form.vin, onVinChange = { form = form.copy(vin = it) }, onDecoded = { form = form.apply(it) })

        SectionLabel("Vehicle")
        IdentityPickers(records.vehicleCatalog, form, onChange = { form = it })
        LabeledTextField("Trim", form.trim, { form = form.copy(trim = it) }, capitalization = KeyboardCapitalization.Words)
        MileageField("Current mileage", form.miles, { form = form.copy(miles = it) })

        SectionLabel("Body")
        SuggestField("Body style", form.bodyStyle, { form = form.copy(bodyStyle = it) }, BODY_STYLES)
        if (form.isPickup) {
            Row {
                SuggestField("Cab", form.cabStyle, { form = form.copy(cabStyle = it) }, CAB_STYLES, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SuggestField("Bed", form.bedLength, { form = form.copy(bedLength = it) }, BED_LENGTHS, modifier = Modifier.weight(1f))
            }
        }

        SectionLabel("Engine")
        SuggestField("Fuel", form.fuelType, { form = form.copy(fuelType = it) }, FUEL_TYPES)
        if (!form.isElectric) {
            Row {
                SuggestField(
                    "Liters",
                    form.displacementL,
                    { form = form.copy(displacementL = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                    emptyList(),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SuggestField(
                    "Cylinders",
                    form.cylinders,
                    { form = form.copy(cylinders = it.filter(Char::isDigit)) },
                    CYLINDERS,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            SuggestField("Aspiration", form.aspiration, { form = form.copy(aspiration = it) }, ASPIRATIONS)
        }
        LabeledTextField(
            "Engine description (optional)",
            form.engine,
            { form = form.copy(engine = it) },
            singleLine = false,
        )

        SectionLabel("Transmission")
        SegmentedControl(
            options = TRANSMISSION_TYPES,
            selected = form.transmissionType,
            // Tapping the selected option again clears it: "not sure" has to be expressible.
            onSelect = { form = form.copy(transmissionType = if (it == form.transmissionType) "" else it) },
        )
        if (form.transmissionType != "CVT") {
            SuggestField(
                "Speeds",
                form.transmissionSpeeds,
                { form = form.copy(transmissionSpeeds = it.filter(Char::isDigit)) },
                speedOptions(form.transmissionType),
                keyboardType = KeyboardType.Number,
            )
        }

        SectionLabel("Drivetrain")
        SegmentedControl(
            options = DRIVETRAINS,
            selected = form.drivetrain,
            onSelect = { form = form.copy(drivetrain = if (it == form.drivetrain) "" else it) },
        )
        if (form.drivetrain.isNotBlank() && form.drivetrain !in DRIVETRAINS) {
            // An older free-text value that doesn't map onto the buttons stays visible and kept.
            Text("Currently: ${form.drivetrain}", style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
        }

        LabeledTextField("Role", form.role, { form = form.copy(role = it) }, singleLine = false, minLines = 2)

        ActionLink(
            if (showMoreDetails) "Hide more details" else "Show more details",
            onClick = { showMoreDetails = !showMoreDetails },
            modifier = Modifier.padding(top = 12.dp),
        )
        if (showMoreDetails) {
            LabeledTextField("Color", form.color, { form = form.copy(color = it) }, capitalization = KeyboardCapitalization.Words)
            LabeledTextField("Free-form notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 2)
        }

        ActionLink(
            if (showSevereDuty) "Hide severe-duty conditions" else "Show severe-duty conditions",
            onClick = { showSevereDuty = !showSevereDuty },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (showSevereDuty) {
            Text(
                "Any of these checked halves computed maintenance intervals, per typical OEM severe-duty schedules.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            CheckboxRow("Driving in dusty areas", form.severeDustyAreas) { form = form.copy(severeDustyAreas = it) }
            CheckboxRow("Towing a trailer", form.severeTowing) { form = form.copy(severeTowing = it) }
            CheckboxRow("Idling for extended periods", form.severeExtendedIdling) { form = form.copy(severeExtendedIdling = it) }
            CheckboxRow("Low speed / short trips in below-freezing temps", form.severeLowSpeedColdWeather) { form = form.copy(severeLowSpeedColdWeather = it) }
            CheckboxRow("Heavy city traffic above 90°F", form.severeHeavyCityTrafficHot) { form = form.copy(severeHeavyCityTrafficHot = it) }
            CheckboxRow("Hilly/mountainous terrain above 90°F", form.severeMountainousHot) { form = form.copy(severeMountainousHot = it) }
            CheckboxRow("Frequent trailer towing", form.severeFrequentTowing) { form = form.copy(severeFrequentTowing = it) }
            CheckboxRow("Driven through deep water", form.severeDeepWater) { form = form.copy(severeDeepWater = it) }
        }

        if (vehicle == null) {
            SectionLabel("Starter maintenance schedule")
            Text(
                "Tick what to track and adjust the intervals, or have $ASSISTANT_NAME look up this vehicle's own schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
            )
            BobScheduleButton(
                bob = bob,
                runKey = scheduleKey(stableId),
                ready = form.year.isNotBlank() && form.make.isNotBlank() && form.model.isNotBlank(),
                onRun = {
                    bob.suggestSchedule(buildEntity(), alreadyTracked = emptyList()) { suggestions ->
                        services = suggestions.map {
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
            ServiceChecklist(services, onChange = { services = it })
        }
    }
}

/**
 * Year → make → model, each narrowing the next, then the engine/transmission combinations that
 * fueleconomy.gov lists for that model. Picking a combination fills the spec fields below it.
 * Lookups are debounced so typing "Silverado" doesn't fire nine requests.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
}

@Composable
private fun VehiclePhotoPicker(
    photoPath: String?,
    onPhotoPicked: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onPhotoPicked(uri)
    }
    val hasPhoto = photoPath != null

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            if (photoPath != null) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionLink(
                if (hasPhoto) "Change photo" else "Add a photo",
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            if (hasPhoto) {
                ActionLink("Remove photo", onClick = onRemovePhoto, tint = garageColors.alarmText)
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
