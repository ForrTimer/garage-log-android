package com.garagelog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.theme.garageColors

@Composable
fun VehiclePickerRow(vehicles: List<VehicleEntity>, activeVehicleId: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        item { VehicleChip(text = "All", selected = activeVehicleId == null, onClick = { onSelect(null) }) }
        items(vehicles, key = { it.id }) { v ->
            VehicleChip(text = v.name, selected = v.id == activeVehicleId, onClick = { onSelect(v.id) })
        }
    }
}

@Composable
private fun VehicleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RectangleShape,
        color = if (selected) colors.primary else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outline),
        modifier = Modifier.defaultMinSize(minHeight = 40.dp).clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

enum class PillTone { Open, Progress, Resolved, Upcoming }

@Composable
private fun PillTone.color(): Color = when (this) {
    PillTone.Open -> garageColors.alarmText
    PillTone.Progress -> garageColors.warn
    PillTone.Resolved -> garageColors.ok
    PillTone.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun PillBadge(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val color = tone.color()
    Surface(
        shape = RectangleShape,
        color = color.copy(alpha = garageColors.pillTintAlpha),
        contentColor = color,
        modifier = modifier,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun GarageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun StatGrid(stats: List<Pair<String, String>>) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        stats.forEachIndexed { index, (value, label) ->
            if (index != 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = if (index == 0) 0.dp else 14.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = garageColors.textMuted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp)) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = garageColors.alarmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RectangleShape,
                color = if (isSelected) colors.primary else colors.surfaceVariant,
                contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).clickable { onSelect(option) },
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}
