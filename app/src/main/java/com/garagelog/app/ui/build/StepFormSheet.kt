package com.garagelog.app.ui.build

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
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.StepPriority
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.ReadOnlyDropdownField
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

    FormSheetScaffold(
        title = if (step == null) "New build step" else "Edit build step",
        onDismiss = onDismiss,
        showDelete = step != null,
        deleteTitle = "Delete build step?",
        onDelete = { step?.let { onDelete(it.id) } },
        onSave = {
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
    ) {
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
        val selectedPhaseId = if (form.manualPhaseOverride) form.phaseId else null
        val autoAssignedPhaseName = phases.find { it.id == form.phaseId }?.phase
        ReadOnlyDropdownField(
            displayValue = phases.find { it.id == selectedPhaseId }?.phase
                ?: autoAssignedPhaseName?.let { "Auto ($it)" }
                ?: "Auto",
            options = listOf<BuildPhaseEntity?>(null) + phases.sortedBy { it.order },
            optionLabel = { it?.phase ?: "Auto-assign" },
            onSelect = { option -> form = form.copy(phaseId = option?.id, manualPhaseOverride = option != null) },
        )

        if (step != null) {
            PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.BUILD_STEP, ownerId = step.id)
        }
    }
}
