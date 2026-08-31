package com.garagelog.app.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SectionTitle

private fun statusTone(status: String): PillTone = when (status) {
    PhaseStatus.Done.label -> PillTone.Resolved
    PhaseStatus.InProgress.label -> PillTone.Progress
    else -> PillTone.Open
}

@Composable
fun BuildScreen(uiState: GarageLogUiState, onItemClick: (BuildPhaseEntity) -> Unit) {
    val vehicles = (uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles)
        .filter { v -> uiState.buildPhases.any { it.vehicleId == v.id } }

    LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp)) {
        if (vehicles.isEmpty()) {
            item {
                val vehicleName = uiState.activeVehicle?.name
                EmptyState("No build phases tracked${vehicleName?.let { " for $it" } ?: ""} yet. Tap + to add one — useful for a rebuild project.")
            }
        }
        vehicles.forEach { v ->
            item { SectionTitle(v.name) }
            item {
                val phases = uiState.buildPhases.filter { it.vehicleId == v.id }.sortedBy { it.order }
                GarageCard {
                    phases.forEachIndexed { index, phase ->
                        PhaseRow(phase, onClick = { onItemClick(phase) })
                        if (index != phases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseRow(phase: BuildPhaseEntity, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(phase.phase, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillBadge(text = phase.status, tone = statusTone(phase.status))
            }
            if (phase.notes.isNotBlank()) {
                Text(phase.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
