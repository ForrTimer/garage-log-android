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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.StatGrid
import com.garagelog.app.ui.components.SectionTitle
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.DrivingRate
import com.garagelog.app.util.ProjectedService
import com.garagelog.app.util.drivingRate
import com.garagelog.app.util.projectSchedules
import com.garagelog.app.util.fillUpMpg
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney
import com.garagelog.app.util.monthName
import kotlin.math.roundToInt

/**
 * What each vehicle is costing and what it needs next, rather than a wall of charts.
 *
 * Ordered by what you'd actually want first: the headline numbers, then what's coming due, then
 * the history behind them. Each section states its own point in a heading, so nothing depends on
 * reading a colour to know what it is.
 *
 * Everything degrades honestly when the data isn't there. Running costs need a driving rate, and
 * MPG needs consecutive full tanks — with one odometer reading or a single fill-up there is no
 * trend to draw, and inventing one would put a confident wrong number on the screen.
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
            val rate = drivingRate(logs)
            item { SectionTitle(v.name) }
            item { RunningCostCard(vehicle = v, logs = logs, rate = rate) }
            item { CostTrendCard(logs) }
            item { FuelEconomyCard(logs) }
            item { OdometerCard(logs) }
        }
    }
}

/**
 * The headline numbers. A single figure is a stat, not a chart — these are the four things worth
 * knowing at a glance, and none of them is worth a plot of its own.
 */
@Composable
private fun RunningCostCard(vehicle: VehicleEntity, logs: List<LogEntryEntity>, rate: DrivingRate?) {
    val (fuelLogs, serviceLogs) = logs.partition { it.category == LogCategory.Fuel.name }
    val totalSpend = logs.sumOf { it.cost ?: 0.0 }
    val mpg = fillUpMpg(logs)

    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Running costs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Cost per mile is only honest over the miles actually covered by the logged history —
        // dividing lifetime spend by total odometer would credit the truck with 200k miles of
        // someone else's ownership.
        val costPerMile = rate?.basisMiles?.takeIf { it > 0 && totalSpend > 0 }?.let { totalSpend / it }

        StatGrid(
            listOf(
                (costPerMile?.let { "$${"%.2f".format(it)}" } ?: "—") to "per mile",
                (rate?.let { "${it.milesPerDay.roundToInt()}" } ?: "—") to "miles / day",
                (mpg.lastOrNull()?.let { "%.1f".format(it.mpg) } ?: "—") to "recent mpg",
                formatMoney(totalSpend) to "logged total",
            ),
        )

        Text(
            text = when {
                rate == null ->
                    "Log a second mileage reading to work out what this vehicle costs to run."
                costPerMile == null ->
                    "Based on ${formatMiles(rate.basisMiles)} over ${rate.basisDays} days. Add costs to your log entries for a per-mile figure."
                else ->
                    "Fuel and service across ${formatMiles(rate.basisMiles)} and ${rate.basisDays} days " +
                        "(${rate.readings} readings). Service ${formatMoney(serviceLogs.sumOf { it.cost ?: 0.0 })} · " +
                        "fuel ${formatMoney(fuelLogs.sumOf { it.cost ?: 0.0 })}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
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

@Composable
private fun FuelEconomyCard(logs: List<LogEntryEntity>) {
    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Fuel economy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val all = fillUpMpg(logs)
        // Plenty of points still read as a trend on a line, so keep more of the history than a
        // bar chart could fit.
        val shown = all.takeLast(24)
        when {
            all.isEmpty() -> EmptyState(
                "Log full-tank fill-ups to see MPG here — partial fills don't give an accurate reading.",
                icon = Icons.Filled.ShowChart,
            )
            // One reading is a number, not a trend; drawing a single bar implies a shape that
            // isn't there yet.
            all.size == 1 -> Text(
                "${"%.1f".format(all.first().mpg)} mpg on the last full tank. One more full-tank " +
                    "fill-up and this becomes a trend.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> {
                val average = all.map { it.mpg }.average()
                Text(
                    "${"%.1f".format(average)} mpg average over ${all.size} full tanks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // A line, because the question is which way economy is moving — and unlike bars it
                // stays readable whether there are three points or thirty, so the chart grows with
                // the history instead of needing a different form later.
                LineChart(
                    values = shown.map { it.mpg },
                    leadingLabel = formatDate(shown.first().date),
                    trailingLabel = formatDate(shown.last().date),
                    valueLabel = { "%.1f".format(it) },
                )
            }
        }
    }
}

@Composable
private fun CostTrendCard(logs: List<LogEntryEntity>) {
    val costed = logs.filter { it.cost != null }
    // Fuel is charted on its own rather than folded into the monthly total: it recurs on a
    // completely different cadence to maintenance, and averaging the two together hides both.
    val (fuelCosted, serviceCosted) = costed.partition { it.category == LogCategory.Fuel.name }
    GarageCard {
        Text("Cost", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (costed.isEmpty()) {
            EmptyState("No costed log entries yet.", icon = Icons.Filled.ShowChart)
        } else {
            // Two series, so each gets a named heading and its own colour. Blue and amber are
            // chosen as the pair because blue-vs-orange is the split every common form of colour
            // blindness preserves; the headings mean identity never rests on the colour anyway.
            if (serviceCosted.isNotEmpty()) {
                SeriesHeading(
                    label = "Service",
                    total = serviceCosted.sumOf { it.cost ?: 0.0 },
                    months = monthsSpanned(serviceCosted),
                    color = MaterialTheme.colorScheme.primary,
                    topPadding = 4.dp,
                )
                MonthlySpendChart(serviceCosted, MaterialTheme.colorScheme.primary)
            }
            if (fuelCosted.isNotEmpty()) {
                SeriesHeading(
                    label = "Fuel",
                    total = fuelCosted.sumOf { it.cost ?: 0.0 },
                    months = monthsSpanned(fuelCosted),
                    color = garageColors.warn,
                    topPadding = 16.dp,
                )
                MonthlySpendChart(fuelCosted, garageColors.warn)
            }
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

/** Distinct calendar months with activity — the denominator for a per-month average. */
private fun monthsSpanned(logs: List<LogEntryEntity>): Int =
    logs.filter { it.date.length >= 7 }.map { it.date.substring(0, 7) }.distinct().size

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

/**
 * A coloured swatch beside the series name, so the chart's colour has a stated meaning — plus a
 * per-month average, which is the figure that makes two totals over different spans comparable.
 */
@Composable
private fun SeriesHeading(label: String, total: Double, months: Int, color: Color, topPadding: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            buildString {
                append("$label — ${formatMoney(total)} total")
                if (months > 0) append(" · ${formatMoney(total / months)}/mo avg")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun MonthlySpendChart(logs: List<LogEntryEntity>, barColor: Color) {
    val totals = monthlyTotals(logs)
    BarChart(
        bars = totals.map { it.second.toFloat() },
        labels = totals.map { monthLabel(it.first) },
        valueLabel = { formatMoney(it.toDouble()).removeSuffix(".00") },
        barColor = barColor,
    )
}

/**
 * Shares of the whole rather than dollar amounts — the totals are already stated above, and the
 * question this answers is "where is the money going". The bars are scaled to the total for the
 * same reason: scaled to the largest category instead, every slice would look overstated.
 */
@Composable
private fun CategoryBreakdown(logs: List<LogEntryEntity>) {
    val byCategory = logs.groupBy { it.category }
        .mapValues { (_, entries) -> entries.sumOf { it.cost ?: 0.0 } }
        .toList()
        .sortedByDescending { it.second }
    val grandTotal = byCategory.sumOf { it.second }
    if (grandTotal <= 0) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        byCategory.forEach { (category, total) ->
            val share = total / grandTotal
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
                            .fillMaxWidth(share.toFloat().coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    "${(share * 100).roundToInt()}%",
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
private fun BarChart(
    bars: List<Float>,
    labels: List<String>,
    valueLabel: (Float) -> String,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
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
                            // A fixed width for a handful of bars, proportional once there are
                            // enough to crowd: with one or two, 55% of a half-screen column draws
                            // a square block rather than a bar. (A max-width constraint can't do
                            // this — fillMaxWidth sets an exact width, so the minimum wins.)
                            .then(
                                if (bars.size <= 4) Modifier.width(48.dp) else Modifier.fillMaxWidth(0.55f),
                            )
                            .fillMaxHeight(fraction)
                            .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
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
