package com.garagelog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
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
