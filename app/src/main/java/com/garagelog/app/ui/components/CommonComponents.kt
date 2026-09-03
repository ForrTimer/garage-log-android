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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.theme.GarageCardShape
import com.garagelog.app.ui.theme.GarageChipShape
import com.garagelog.app.ui.theme.GaragePillShape
import com.garagelog.app.ui.theme.garageColors
import kotlinx.coroutines.launch

@Composable
fun VehiclePickerRow(vehicles: List<VehicleEntity>, activeVehicleId: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        item { SelectableChip(text = "All", selected = activeVehicleId == null, onClick = { onSelect(null) }) }
        items(vehicles, key = { it.id }) { v ->
            SelectableChip(text = v.name, selected = v.id == activeVehicleId, onClick = { onSelect(v.id) })
        }
    }
}

/** A horizontally scrolling "All" + one-of-[options] chip row, e.g. category/status filters. */
@Composable
fun ChipFilterRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        item { SelectableChip(text = "All", selected = selected == null, onClick = { onSelect(null) }) }
        items(options) { option ->
            SelectableChip(text = option, selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = GarageChipShape,
        color = if (selected) colors.primary else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

enum class PillTone { Open, Progress, Resolved, Upcoming, Info, Neutral }

@Composable
private fun PillTone.color(): Color = when (this) {
    PillTone.Open -> garageColors.alarmText
    PillTone.Progress -> garageColors.warn
    PillTone.Resolved -> garageColors.ok
    PillTone.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
    PillTone.Info -> garageColors.info
    PillTone.Neutral -> garageColors.textMuted
}

@Composable
fun PillBadge(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val color = tone.color()
    Surface(
        shape = GaragePillShape,
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
        shape = GarageCardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun StatGrid(stats: List<Pair<String, String>>, onItemClick: ((Int) -> Unit)? = null) {
    if (onItemClick != null) {
        // Each stat reads as its own tappable tile, so all three need identical, symmetric
        // spacing on every side — a shared divider between plain-text columns (below) would
        // sit at an inconsistent distance from each tile's box edge.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            stats.forEachIndexed { index, (value, label) ->
                Column(
                    modifier = Modifier.weight(1f)
                        .clip(GarageChipShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = garageColors.pillTintAlpha))
                        .clickable { onItemClick(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
                }
            }
        }
    } else {
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
                Column(modifier = Modifier.weight(1f).padding(horizontal = if (index == 0) 0.dp else 14.dp)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
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
        color = garageColors.textMuted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(text: String, icon: ImageVector? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = garageColors.textMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp).padding(bottom = 10.dp),
            )
        }
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
    confirmColor: Color = garageColors.alarmText,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = confirmColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Swipe-left-to-delete for a list row — reveals a red trash affordance, then gates the actual
 * delete behind the same [ConfirmDialog] every other destructive action in the app uses (rather
 * than an instant swipe-delete + undo-snackbar, a different paradigm this app doesn't use
 * elsewhere). A completed swipe opens the dialog; canceling resets the row back into view, only
 * confirming actually removes it. (confirmValueChange-as-veto is deprecated in this Material3
 * version and no longer reliably prevents the row settling into the swiped state, hence driving
 * this off currentValue + an explicit reset instead.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    deleteTitle: String = "Delete this entry?",
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) showConfirm = true
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(garageColors.alarmText.copy(alpha = 0.16f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = garageColors.alarmText)
            }
        },
    ) {
        // SwipeToDismissBox only hides backgroundContent behind whatever the foreground
        // actually paints — it isn't clipped by swipe progress. Row content here (Text/PillBadge)
        // doesn't fill its own bounds opaquely, so without this the delete icon bled through at
        // rest everywhere the row itself was "empty" space, not just while mid-swipe.
        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) { content() }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = deleteTitle,
            message = "This can't be undone.",
            onConfirm = {
                onDelete()
                showConfirm = false
            },
            onDismiss = {
                showConfirm = false
                scope.launch { dismissState.reset() }
            },
        )
    }
}

/**
 * A small filled color-box "button" for a secondary tap action (Edit, Update mileage, etc.) —
 * replaces plain underlined text so it's unambiguous that the label is tappable.
 */
@Composable
fun ActionLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        shape = GarageChipShape,
        color = tint.copy(alpha = garageColors.pillTintAlpha),
        contentColor = tint,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = GarageChipShape,
                color = if (isSelected) colors.primary else colors.surfaceVariant,
                contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline),
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Box(
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
    )
}
