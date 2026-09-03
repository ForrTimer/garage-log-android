package com.garagelog.app.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.ChipFilterRow
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney

// Log entries are always things already done, not open problems — Repair shouldn't wear the
// same alarm red as an overdue/open issue. Upgrade gets its own blue so it reads as its own kind
// of entry rather than reusing the neutral "upcoming" gray.
private fun categoryTone(category: String): PillTone = when (category) {
    LogCategory.Routine.name -> PillTone.Resolved
    LogCategory.Repair.name -> PillTone.Neutral
    LogCategory.Upgrade.name -> PillTone.Info
    else -> PillTone.Progress
}

@Composable
fun LogScreen(uiState: GarageLogUiState, onItemClick: (LogEntryEntity) -> Unit) {
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    val logs = uiState.logsFor(uiState.activeVehicleId)
        .filter { categoryFilter == null || it.category == categoryFilter }
        .sortedByDescending { it.date }

    LazyColumn(contentPadding = GarageDimens.listContentPaddingWithFab) {
        item {
            ChipFilterRow(
                options = LogCategory.entries.map { it.name },
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
        }
        item {
            GarageCard(modifier = Modifier.padding(top = 12.dp)) {
                if (logs.isEmpty()) {
                    EmptyState(
                        if (categoryFilter != null) "No $categoryFilter entries." else "No maintenance entries yet. Tap + to log a service.",
                    )
                } else {
                    logs.forEachIndexed { index, entry ->
                        LogRow(entry, uiState, onClick = { onItemClick(entry) })
                        if (index != logs.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntryEntity, uiState: GarageLogUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(entry.task, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillBadge(text = entry.category, tone = categoryTone(entry.category))
            }
            val subtitleParts = buildList {
                if (uiState.activeVehicleId == null) add(uiState.vehicleName(entry.vehicleId))
                add(formatDate(entry.date))
                add(formatMiles(entry.mileage))
                if (entry.cost != null) add(formatMoney(entry.cost))
            }
            Text(subtitleParts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
