package com.garagelog.app.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.formatMoney

private fun statusTone(status: String): PillTone = when (status) {
    PhaseStatus.Done.label -> PillTone.Resolved
    PhaseStatus.InProgress.label -> PillTone.Progress
    else -> PillTone.Open
}

@Composable
fun BuildScreen(
    uiState: GarageLogUiState,
    onPhaseClick: (BuildPhaseEntity) -> Unit,
    onStepClick: (BuildStepEntity) -> Unit,
    onAddPhase: (vehicleId: String) -> Unit,
    onImportStepsFromNotes: (BuildPhaseEntity) -> Unit,
) {
    val vehicles = (uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles)
        .filter { v -> uiState.buildPhases.any { it.vehicleId == v.id } || uiState.buildSteps.any { it.vehicleId == v.id } }

    LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 88.dp)) {
        if (vehicles.isEmpty()) {
            item {
                val vehicleName = uiState.activeVehicle?.name
                EmptyState("No build plan${vehicleName?.let { " for $it" } ?: ""} yet. Tap + to add a step — useful for a rebuild project.")
            }
        }
        vehicles.forEach { v ->
            item { SectionTitle(v.name) }
            val phases = uiState.buildPhases.filter { it.vehicleId == v.id }.sortedBy { it.order }
            val steps = uiState.buildSteps.filter { it.vehicleId == v.id }
            phases.forEach { phase ->
                item {
                    PhaseCard(
                        phase,
                        steps.filter { it.phaseId == phase.id },
                        onPhaseClick = { onPhaseClick(phase) },
                        onStepClick = onStepClick,
                        onImportStepsFromNotes = { onImportStepsFromNotes(phase) },
                    )
                }
            }
            val unbucketed = steps.filter { it.phaseId == null }
            if (unbucketed.isNotEmpty()) {
                item {
                    GarageCard {
                        Text("Not yet bucketed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Doesn't fit any phase's priority/budget — edit the step or add a phase for it.",
                            color = garageColors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        unbucketed.forEachIndexed { index, step ->
                            StepRow(step, onClick = { onStepClick(step) })
                            if (index != unbucketed.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "+ Add phase",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp).clickable { onAddPhase(v.id) },
                )
            }
        }
    }
}

@Composable
private fun PhaseCard(
    phase: BuildPhaseEntity,
    steps: List<BuildStepEntity>,
    onPhaseClick: () -> Unit,
    onStepClick: (BuildStepEntity) -> Unit,
    onImportStepsFromNotes: () -> Unit,
) {
    var expanded by remember(phase.id) { mutableStateOf(true) }
    val spent = steps.sumOf { (if (it.status == PhaseStatus.Done.label) it.actualCost ?: it.estimatedCost else it.estimatedCost) ?: 0.0 }

    GarageCard {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(phase.phase, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val criteria = buildList {
                    phase.priorityFilter?.let { add("$it priority") }
                    phase.budgetCap?.let { add("${formatMoney(spent)} / ${formatMoney(it)}") } ?: if (spent > 0) add(formatMoney(spent)) else Unit
                }.joinToString(" · ")
                if (criteria.isNotBlank()) Text(criteria, color = garageColors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            PillBadge(text = phase.status, tone = statusTone(phase.status))
        }
        if (phase.notes.isNotBlank()) {
            Text(phase.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                "Edit phase",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onPhaseClick),
            )
            if (phase.notes.isNotBlank() && steps.isEmpty()) {
                Spacer(Modifier.width(16.dp))
                Text(
                    "Import steps from notes",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onImportStepsFromNotes),
                )
            }
        }
        if (expanded) {
            if (steps.isEmpty()) {
                Text(
                    "No steps in this phase yet.",
                    color = garageColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    steps.sortedBy { it.order }.forEachIndexed { index, step ->
                        StepRow(step, onClick = { onStepClick(step) })
                        if (index != steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: BuildStepEntity, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyMedium)
            val cost = (if (step.status == PhaseStatus.Done.label) step.actualCost ?: step.estimatedCost else step.estimatedCost)
            val meta = listOfNotNull(step.priority.takeIf { it != "Medium" }?.let { "$it priority" }, cost?.let { formatMoney(it) })
                .joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = garageColors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        PillBadge(text = step.status, tone = statusTone(step.status))
    }
}
