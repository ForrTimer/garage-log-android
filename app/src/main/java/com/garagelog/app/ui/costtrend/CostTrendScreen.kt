package com.garagelog.app.ui.costtrend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.Accent
import com.garagelog.app.ui.theme.Accent2
import com.garagelog.app.ui.theme.Surface2
import com.garagelog.app.ui.theme.TextDim
import com.garagelog.app.util.formatMoney
import java.util.Locale

@Composable
fun CostTrendScreen(uiState: GarageLogUiState, onBack: () -> Unit) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Cost trend", style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
            if (vehicles.isEmpty()) {
                item { EmptyState("No vehicles yet.") }
            }
            vehicles.forEach { v ->
                val logs = uiState.logs.filter { it.vehicleId == v.id && it.cost != null }
                item { SectionTitle(v.name) }
                item {
                    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text("Monthly spend", style = MaterialTheme.typography.labelMedium, color = TextDim)
                        if (logs.isEmpty()) {
                            EmptyState("No costed log entries yet.")
                        } else {
                            MonthlySpendChart(logs)
                        }
                    }
                }
                item {
                    GarageCard {
                        Text("By category", style = MaterialTheme.typography.labelMedium, color = TextDim)
                        if (logs.isEmpty()) {
                            EmptyState("No costed log entries yet.")
                        } else {
                            CategoryBreakdown(logs)
                        }
                    }
                }
            }
        }
    }
}

private fun monthlyTotals(logs: List<LogEntryEntity>): List<Pair<String, Double>> {
    return logs.filter { it.date.length >= 7 }
        .groupBy { it.date.substring(0, 7) }
        .mapValues { (_, entries) -> entries.sumOf { it.cost ?: 0.0 } }
        .toSortedMap()
        .toList()
        .takeLast(6)
}

private fun monthLabel(yyyyMm: String): String {
    val parts = yyyyMm.split("-")
    if (parts.size != 2) return yyyyMm
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yyyyMm
    return if (monthIndex in monthNames.indices) monthNames[monthIndex] else yyyyMm
}

@Composable
private fun MonthlySpendChart(logs: List<LogEntryEntity>) {
    val totals = monthlyTotals(logs)
    val maxValue = totals.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 10.dp),
    ) {
        totals.forEach { (month, total) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Text(formatMoney(total).removeSuffix(".00"), style = MaterialTheme.typography.bodySmall, color = TextDim)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (total / maxValue).toFloat().coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .background(Accent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
                Text(monthLabel(month), style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(logs: List<LogEntryEntity>) {
    val byCategory = logs.groupBy { it.category }
        .mapValues { (_, entries) -> entries.sumOf { it.cost ?: 0.0 } }
        .toList()
        .sortedByDescending { it.second }
    val maxValue = byCategory.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        byCategory.forEach { (category, total) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                Box(modifier = Modifier.weight(1f).height(10.dp).background(Surface2, RoundedCornerShape(4.dp))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((total / maxValue).toFloat().coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .background(Accent2, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    formatMoney(total),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    modifier = Modifier.padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
