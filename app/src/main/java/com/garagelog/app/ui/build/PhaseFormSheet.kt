package com.garagelog.app.ui.build

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
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageColors
import java.util.UUID

private data class PhaseFormState(
    val vehicleId: String,
    val phase: String,
    val status: String,
    val order: String,
    val notes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseFormSheet(
    phase: BuildPhaseEntity?,
    vehicles: List<VehicleEntity>,
    defaultVehicleId: String?,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onSave: (BuildPhaseEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var form by remember(phase?.id) {
        mutableStateOf(
            PhaseFormState(
                vehicleId = phase?.vehicleId ?: defaultVehicleId ?: "",
                phase = phase?.phase ?: "",
                status = phase?.status ?: PhaseStatus.NotStarted.label,
                order = (phase?.order ?: nextOrder).toString(),
                notes = phase?.notes ?: "",
            ),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding()) {
            Text(if (phase == null) "New build phase" else "Edit build phase", style = MaterialTheme.typography.titleLarge)

            VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
            LabeledTextField("Phase name", form.phase, { form = form.copy(phase = it) })

            Text("Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
            SegmentedControl(
                options = listOf(PhaseStatus.NotStarted.label, PhaseStatus.InProgress.label, PhaseStatus.Done.label),
                selected = form.status,
                onSelect = { form = form.copy(status = it) },
            )

            LabeledTextField("Order (lower = earlier)", form.order, { form = form.copy(order = it) }, keyboardType = KeyboardType.Number)
            LabeledTextField("Notes / scope", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 3)

            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp)) {
                if (phase != null) {
                    OutlinedButton(onClick = { onDelete(phase.id) }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {
                        onSave(
                            BuildPhaseEntity(
                                id = phase?.id ?: UUID.randomUUID().toString(),
                                vehicleId = form.vehicleId,
                                phase = form.phase.trim().ifBlank { "Untitled phase" },
                                status = form.status,
                                order = form.order.trim().toIntOrNull() ?: 0,
                                notes = form.notes.trim(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}
