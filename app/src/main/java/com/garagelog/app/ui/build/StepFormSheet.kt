package com.garagelog.app.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.StepPriority
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.theme.garageColors
import java.util.UUID

private data class StepFormState(
    val title: String,
    val notes: String,
    val priority: String,
    val status: String,
    val estimatedCost: String,
    val actualCost: String,
    val phaseId: String?,
    val manualPhaseOverride: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepFormSheet(
    step: BuildStepEntity?,
    vehicleId: String,
    phases: List<BuildPhaseEntity>,
    nextOrder: Int,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onSave: (BuildStepEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var form by remember(step?.id) {
        mutableStateOf(
            StepFormState(
                title = step?.title ?: "",
                notes = step?.notes ?: "",
                priority = step?.priority ?: StepPriority.Medium.name,
                status = step?.status ?: PhaseStatus.NotStarted.label,
                estimatedCost = step?.estimatedCost?.toString() ?: "",
                actualCost = step?.actualCost?.toString() ?: "",
                phaseId = step?.phaseId,
                manualPhaseOverride = step?.manualPhaseOverride ?: false,
            ),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding(),
        ) {
            Text(if (step == null) "New build step" else "Edit build step", style = MaterialTheme.typography.titleLarge)

            LabeledTextField("Step", form.title, { form = form.copy(title = it) })

            Text("Priority", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
            SegmentedControl(
                options = listOf(StepPriority.Low.name, StepPriority.Medium.name, StepPriority.High.name),
                selected = form.priority,
                onSelect = { form = form.copy(priority = it) },
            )

            Text("Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
            SegmentedControl(
                options = listOf(PhaseStatus.NotStarted.label, PhaseStatus.InProgress.label, PhaseStatus.Done.label),
                selected = form.status,
                onSelect = { form = form.copy(status = it) },
            )

            LabeledTextField(
                "Estimated cost (\$)", form.estimatedCost, { form = form.copy(estimatedCost = it) },
                keyboardType = KeyboardType.Decimal,
            )
            LabeledTextField(
                "Actual cost (\$) — once done", form.actualCost, { form = form.copy(actualCost = it) },
                keyboardType = KeyboardType.Decimal,
            )
            LabeledTextField("Notes", form.notes, { form = form.copy(notes = it) }, singleLine = false, minLines = 3)

            Text(
                "Phase",
                style = MaterialTheme.typography.labelMedium,
                color = garageColors.textMuted,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "Auto-bucketed by priority and each phase's budget. Pick one below to pin it there instead.",
                style = MaterialTheme.typography.bodySmall,
                color = garageColors.textMuted,
            )
            PhaseDropdown(
                phases = phases,
                selectedId = if (form.manualPhaseOverride) form.phaseId else null,
                autoAssignedPhaseName = phases.find { it.id == form.phaseId }?.phase,
                onSelect = { form = form.copy(phaseId = it, manualPhaseOverride = it != null) },
            )

            if (step != null) {
                PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.BUILD_STEP, ownerId = step.id)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp)) {
                if (step != null) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {
                        onSave(
                            BuildStepEntity(
                                id = step?.id ?: UUID.randomUUID().toString(),
                                vehicleId = vehicleId,
                                phaseId = if (form.manualPhaseOverride) form.phaseId else step?.phaseId,
                                title = form.title.trim().ifBlank { "Untitled step" },
                                notes = form.notes.trim(),
                                priority = form.priority,
                                status = form.status,
                                estimatedCost = form.estimatedCost.trim().toDoubleOrNull(),
                                actualCost = form.actualCost.trim().toDoubleOrNull(),
                                order = step?.order ?: nextOrder,
                                manualPhaseOverride = form.manualPhaseOverride,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }

    if (showDeleteConfirm && step != null) {
        ConfirmDialog(
            title = "Delete build step?",
            message = "This can't be undone.",
            onConfirm = { onDelete(step.id) },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun PhaseDropdown(
    phases: List<BuildPhaseEntity>,
    selectedId: String?,
    autoAssignedPhaseName: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = phases.find { it.id == selectedId }?.phase
        ?: autoAssignedPhaseName?.let { "Auto ($it)" }
        ?: "Auto"

    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.85f)) {
            DropdownMenuItem(text = { Text("Auto-assign") }, onClick = { onSelect(null); expanded = false })
            phases.sortedBy { it.order }.forEach { p ->
                DropdownMenuItem(text = { Text(p.phase) }, onClick = { onSelect(p.id); expanded = false })
            }
        }
    }
}
