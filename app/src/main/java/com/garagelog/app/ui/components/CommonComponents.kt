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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.theme.Accent
import com.garagelog.app.ui.theme.AccentOnAccent
import com.garagelog.app.ui.theme.Border
import com.garagelog.app.ui.theme.Danger
import com.garagelog.app.ui.theme.Ok
import com.garagelog.app.ui.theme.Surface2
import com.garagelog.app.ui.theme.TextDim
import com.garagelog.app.ui.theme.Warn

@Composable
fun VehiclePickerRow(vehicles: List<VehicleEntity>, activeVehicleId: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        items(vehicles, key = { it.id }) { v ->
            VehicleChip(text = v.name, selected = v.id == activeVehicleId, onClick = { onSelect(v.id) })
        }
        item { VehicleChip(text = "All", selected = activeVehicleId == null, onClick = { onSelect(null) }) }
    }
}

@Composable
private fun VehicleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Accent else Surface2,
        contentColor = if (selected) AccentOnAccent else TextDim,
        border = BorderStroke(1.dp, if (selected) Accent else Border),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

enum class PillTone(val color: androidx.compose.ui.graphics.Color) {
    Open(Danger), Progress(Warn), Resolved(Ok), Upcoming(Accent)
}

@Composable
fun PillBadge(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.color.copy(alpha = 0.18f),
        contentColor = tone.color,
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun StatGrid(stats: List<Pair<String, String>>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        stats.forEach { (value, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Surface2, RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp, horizontal = 6.dp),
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TextDim, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextDim,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp)) {
        Text(text, color = TextDim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Accent else Surface2,
                contentColor = if (isSelected) AccentOnAccent else TextDim,
                border = BorderStroke(1.dp, if (isSelected) Accent else Border),
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Text(option, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}
