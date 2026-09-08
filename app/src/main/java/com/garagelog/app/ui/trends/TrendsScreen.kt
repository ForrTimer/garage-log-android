package com.garagelog.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney
import com.garagelog.app.util.monthName

/**
 * Replaces the old Build tab — a rebuild-project checklist turned out to be a different app's
 * job (see [[garage-log-roadmap-status]] if this is ever revisited). Higher-rated maintenance
 * trackers lean on visualizing metrics over raw data entry, so this absorbs the old standalone
 * Cost trend screen (monthly spend + by-category) alongside two new charts: odometer progression
 * and fuel economy, both only meaningful once Fuel/Mileage log entries exist to source them from.
 */
@Composable
fun TrendsScreen(uiState: GarageLogUiState) {
    val vehicles = uiState.activeVehicleId?.let { id -> uiState.vehicles.filter { it.id == id } } ?: uiState.vehicles

    LazyColumn(contentPadding = GarageDimens.subScreenContentPadding) {
        if (vehicles.isEmpty()) {
            item { EmptyState("No vehicles yet.", icon = Icons.Filled.DirectionsCar) }
        }
        vehicles.forEach { v ->
            val logs = uiState.logs.filter { it.vehicleId == v.id }
            item { SectionTitle(v.name) }
            item { OdometerCard(logs) }
            item { FuelEconomyCard(logs) }
            item { CostTrendCard(logs) }
        }
    }
}

@Composable
private fun OdometerCard(logs: List<LogEntryEntity>) {
    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Odometer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val points = logs.filter { it.mileage != null }
            .sortedBy { it.date }
            .map { it.date to it.mileage!!.toFloat() }
        if (points.size < 2) {
            EmptyState("Log a couple of mileage readings to see this trend.", icon = Icons.Filled.ShowChart)
        } else {
            LineChart(
                values = points.map { it.second },
                leadingLabel = formatDate(points.first().first),
                trailingLabel = formatDate(points.last().first),
                valueLabel = { formatMiles(it.toInt()) },
            )
        }
    }
}

/**
 * MPG is only trustworthy between two consecutive *full-tank* fill-ups — a partial fill leaves
 * gas in the tank that the math has no way to account for. Gallons from any partial fills in
 * between still count (that fuel was burned too), they just don't get their own MPG point.
 */
private data class FillUp(val date: String, val mpg: Float)

private fun fillUpMpg(logs: List<LogEntryEntity>): List<FillUp> {
    val fuelLogs = logs.filter { it.category == LogCategory.Fuel.name && it.mileage != null && it.gallons != null }
        .sortedBy { it.mileage }
    val result = mutableListOf<FillUp>()
    var lastFullTank: LogEntryEntity? = null
    var gallonsSinceLastFullTank = 0.0
    for (entry in fuelLogs) {
        gallonsSinceLastFullTank += entry.gallons ?: 0.0
        if (entry.fullTank) {
            val previous = lastFullTank
            if (previous != null && previous.mileage != null && entry.mileage != null && gallonsSinceLastFullTank > 0) {
                val miles = entry.mileage - previous.mileage
                if (miles > 0) result.add(FillUp(entry.date, (miles / gallonsSinceLastFullTank).toFloat()))
            }
            lastFullTank = entry
            gallonsSinceLastFullTank = 0.0
        }
    }
    return result
}

@Composable
private fun FuelEconomyCard(logs: List<LogEntryEntity>) {
    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Fuel economy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val fillUps = fillUpMpg(logs).takeLast(8)
        if (fillUps.isEmpty()) {
            EmptyState(
                "Log full-tank fill-ups to see MPG here — partial fills don't give an accurate reading.",
                icon = Icons.Filled.ShowChart,
            )
        } else {
            BarChart(
                bars = fillUps.map { it.mpg },
                labels = fillUps.map { formatDate(it.date).substringBefore(",") },
                valueLabel = { "%.1f".format(it) },
            )
        }
    }
}

@Composable
private fun CostTrendCard(logs: List<LogEntryEntity>) {
    val costed = logs.filter { it.cost != null }
    GarageCard {
        Text("Cost", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (costed.isEmpty()) {
            EmptyState("No costed log entries yet.", icon = Icons.Filled.ShowChart)
        } else {
            MonthlySpendChart(costed)
            Text(
                "By category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            CategoryBreakdown(costed)
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
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yyyyMm
    return monthName(monthIndex).take(3)
}

@Composable
private fun MonthlySpendChart(logs: List<LogEntryEntity>) {
    val totals = monthlyTotals(logs)
    BarChart(
        bars = totals.map { it.second.toFloat() },
        labels = totals.map { monthLabel(it.first) },
        valueLabel = { formatMoney(it.toDouble()).removeSuffix(".00") },
    )
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
                Text(
                    category,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.weight(2f).height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((total / maxValue).toFloat().coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    formatMoney(total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Shared hand-rolled bar chart — same visual language as the old Cost trend screen's charts. */
@Composable
private fun BarChart(bars: List<Float>, labels: List<String>, valueLabel: (Float) -> String) {
    val maxValue = bars.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 10.dp),
    ) {
        bars.forEachIndexed { index, value ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(valueLabel(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (value / maxValue).coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
                Text(labels.getOrElse(index) { "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A minimal line chart — [values] plotted left-to-right, normalized to the card's own range. */
@Composable
private fun LineChart(values: List<Float>, leadingLabel: String, trailingLabel: String, valueLabel: (Float) -> String) {
    val minValue = values.min()
    val maxValue = values.max()
    // Only the on-canvas normalization needs a non-zero span (a flat line would otherwise divide
    // by zero) — the labels above/below the chart must still show the real min/max, or a flat
    // reading (e.g. two mileage entries logged the same day) would display a fabricated value.
    val chartSpan = (maxValue - minValue).coerceAtLeast(1f)
    val lineColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(valueLabel(minValue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel(maxValue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp).padding(vertical = 6.dp)) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, v ->
                val x = index * stepX
                val y = size.height - ((v - minValue) / chartSpan) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            values.forEachIndexed { index, v ->
                val x = index * stepX
                val y = size.height - ((v - minValue) / chartSpan) * size.height
                drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leadingLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(trailingLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
