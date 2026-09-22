package com.garagelog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors

/**
 * The bottom-sheet chrome shared by every "add/edit an entity" sheet (Log, Issue, Schedule,
 * Phase, Step, Vehicle): scrollable sheet + title + entity-specific [content] + a Delete/Save
 * button row + delete-confirmation dialog. Was copy-pasted near-identically into all 6 sheets;
 * factored out here so a change to that shared shape (button spacing, the confirm-dialog
 * wording pattern, etc.) only needs to happen once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    showDelete: Boolean,
    deleteTitle: String,
    onDelete: () -> Unit,
    deleteMessage: String = "This can't be undone.",
    /** Gates Save on the form's own validity; defaults to always-enabled for forms with no rule. */
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberHardToDismissSheetState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // The sheet is a fixed full height with the form scrolling inside it, rather than a single
    // scrolling Column sized to its own content.
    //
    // That's deliberate, and it's what stops the keyboard breaking the layout. With imePadding()
    // inside a content-sized scrolling Column, the sheet's own height changed every time the
    // keyboard opened or closed, so ModalBottomSheet had to re-derive its anchors mid-inset-
    // animation — and when that raced (which depends on the device's IME, hence "works on the
    // emulator, not on the phone") the sheet settled at its keyboard-open height and never grew
    // back. Here imePadding only ever shrinks the scroll viewport; the sheet's height is never a
    // function of the keyboard, so there is nothing to re-derive and nothing to lose the race.
    //
    // Pinning the button row below the scroll area falls out of the same change, and means Save
    // is always reachable without the scroll-to-bottom-on-IME-close workaround this used to need.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            // A fraction rather than the whole screen: still reads as a sheet with the tab
            // underneath showing, but it's a fixed fraction, so it stays keyboard-independent.
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                .padding(horizontal = GarageDimens.sheetHorizontalPadding).imePadding().navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .nestedScroll(SwallowUpwardOverscroll)
                    .verticalScroll(scrollState),
            ) {
                content()
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = GarageDimens.sheetButtonRowTop, bottom = GarageDimens.sheetButtonRowBottom)) {
                if (showDelete) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = garageColors.alarmText)
                    }
                    Spacer(Modifier.width(GarageDimens.sheetButtonSpacing))
                }
                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.weight(1f)) { Text("Save") }
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
 * Once the form is scrolled to its end, whatever upward scroll or fling is left over would
 * otherwise be handed to ModalBottomSheet, which drags the whole sheet up past its anchor and
 * springs it back — the "bounce" at the bottom of a long form (Add vehicle is the one long enough
 * to hit it). Only upward leftovers are eaten: downward ones still reach the sheet, so dragging
 * down from the top of the form can still close it.
 */
private object SwallowUpwardOverscroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
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
