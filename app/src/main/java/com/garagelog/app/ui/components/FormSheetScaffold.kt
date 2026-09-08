package com.garagelog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors

/**
 * The bottom-sheet chrome shared by every "add/edit an entity" sheet (Log, Issue, Schedule,
 * Phase, Step, Vehicle): scrollable sheet + title + entity-specific [content] + a Delete/Save
 * button row + delete-confirmation dialog. Was copy-pasted near-identically into all 6 sheets;
 * factored out here so a change to that shared shape (button spacing, the confirm-dialog
 * wording pattern, etc.) only needs to happen once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    showDelete: Boolean,
    deleteTitle: String,
    onDelete: () -> Unit,
    deleteMessage: String = "This can't be undone.",
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberHardToDismissSheetState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // imePadding() + verticalScroll() on the same Column means the keyboard closing shrinks the
    // content's own height (the padding it was contributing goes away), but the scroll position
    // doesn't reliably re-settle on its own — the Save row (the last thing in the Column) was
    // landing above the visible viewport, requiring a manual scroll down to reach it every time.
    // Explicitly re-settling to the bottom on the closing edge fixes that without guessing at why
    // Compose's own re-layout isn't enough here.
    val imeVisible = WindowInsets.isImeVisible
    var imeHasBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        // Only react to a genuine open-then-close of the keyboard — imeVisible starts false when
        // the sheet first appears too, and that first frame must not itself trigger a jump to
        // the bottom before the user's even seen the top of the form.
        if (imeVisible) {
            imeHasBeenVisible = true
        } else if (imeHasBeenVisible) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
                .padding(horizontal = GarageDimens.sheetHorizontalPadding).imePadding().navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            content()

            Row(modifier = Modifier.fillMaxWidth().padding(top = GarageDimens.sheetButtonRowTop, bottom = GarageDimens.sheetButtonRowBottom)) {
                if (showDelete) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(GarageDimens.sheetButtonSpacing))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = deleteTitle,
            message = deleteMessage,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * A [SheetState] built directly (rather than via `rememberModalBottomSheetState`) so the drag
 * thresholds can be raised above Material3's defaults (56dp positional / 125dp-per-second
 * velocity) — the stock thresholds made it too easy to close a form sheet with an accidental
 * downward drag while scrolling or reaching for a field, losing whatever had been filled in.
 * Roughly 2x both thresholds means a swipe now needs real, deliberate travel (or a genuinely
 * fast flick) to dismiss, while a normal scroll/tap still passes through untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHardToDismissSheetState(): SheetState {
    val density = LocalDensity.current
    val positionalThresholdPx = { with(density) { 120.dp.toPx() } }
    val velocityThresholdPx = { with(density) { 260.dp.toPx() } }
    return rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = positionalThresholdPx,
            velocityThreshold = velocityThresholdPx,
            confirmValueChange = { true },
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = positionalThresholdPx,
            velocityThreshold = velocityThresholdPx,
        )
    }
}
