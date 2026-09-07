package com.garagelog.app.ui.issues

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssuePriority
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.DateField
import com.garagelog.app.ui.components.FormSheetScaffold
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PhotoGridSection
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.components.VehicleDropdown
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.todayIso
import java.util.UUID

private data class IssueFormState(
    val vehicleId: String,
    val title: String,
    val status: String,
    val priority: String,
    val dateOpened: String,
    val dateResolved: String,
    val description: String,
)

@Composable
fun IssueFormSheet(
    issue: IssueEntity?,
    vehicles: List<VehicleEntity>,
    defaultVehicleId: String?,
    viewModel: GarageLogViewModel,
    onDismiss: () -> Unit,
    onSave: (IssueEntity) -> Unit,
    onDelete: (String) -> Unit,
    // Called instead of onSave when the user opts to log how a newly-Resolved issue was fixed —
    // saves the issue AND opens a pre-filled log entry for it, mirroring how marking a
    // maintenance schedule done opens a pre-filled log entry for that.
    onResolvedWithLog: (IssueEntity) -> Unit,
) {
    var form by remember(issue?.id) {
        mutableStateOf(
            IssueFormState(
                vehicleId = issue?.vehicleId ?: defaultVehicleId ?: "",
                title = issue?.title ?: "",
                status = issue?.status ?: IssueStatus.Open.label,
                priority = issue?.priority ?: IssuePriority.Normal.label,
                dateOpened = issue?.dateOpened ?: todayIso(),
                dateResolved = issue?.dateResolved ?: "",
                description = issue?.description ?: "",
            ),
        )
    }

    // The issue was already Resolved coming in (editing an already-fixed issue) vs. this save is
    // the moment it's *becoming* Resolved — only the latter is worth prompting about, since the
    // former already has whatever log entry it was going to get.
    val wasAlreadyResolved = issue?.status == IssueStatus.Resolved.label
    var pendingResolvedIssue by remember { mutableStateOf<IssueEntity?>(null) }

    FormSheetScaffold(
        title = if (issue == null) "New issue" else "Edit issue",
        onDismiss = onDismiss,
        showDelete = issue != null,
        deleteTitle = "Delete issue?",
        onDelete = { issue?.let { onDelete(it.id) } },
        onSave = {
            val entity = IssueEntity(
                id = issue?.id ?: UUID.randomUUID().toString(),
                vehicleId = form.vehicleId,
                title = form.title.trim().ifBlank { "Untitled issue" },
                status = form.status,
                priority = form.priority,
                dateOpened = form.dateOpened.ifBlank { todayIso() },
                dateResolved = form.dateResolved,
                description = form.description.trim(),
            )
            if (!wasAlreadyResolved && form.status == IssueStatus.Resolved.label) {
                pendingResolvedIssue = entity
            } else {
                onSave(entity)
            }
        },
    ) {
        VehicleDropdown("Vehicle", vehicles, form.vehicleId) { form = form.copy(vehicleId = it) }
        LabeledTextField("Title", form.title, { form = form.copy(title = it) })

        Text("Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
        SegmentedControl(
            options = listOf(IssueStatus.Open.label, IssueStatus.InProgress.label, IssueStatus.Resolved.label),
            selected = form.status,
            onSelect = { newStatus ->
                form = form.copy(
                    status = newStatus,
                    dateResolved = if (newStatus == IssueStatus.Resolved.label) form.dateResolved.ifBlank { todayIso() } else "",
                )
            },
        )

        Text("Priority", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
        SegmentedControl(
            options = listOf(IssuePriority.Normal.label, IssuePriority.SafetyCritical.label),
            selected = form.priority,
            onSelect = { form = form.copy(priority = it) },
            accentColorFor = { option -> if (option == IssuePriority.SafetyCritical.label) garageColors.alarm else null },
        )

        DateField("Date opened", form.dateOpened) { form = form.copy(dateOpened = it) }
        LabeledTextField("Description / diagnosis notes", form.description, { form = form.copy(description = it) }, singleLine = false, minLines = 3)

        if (issue != null) {
            PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.ISSUE, ownerId = issue.id)
        }
    }

    // A plain AlertDialog rather than the shared ConfirmDialog — that component always fires
    // its onDismiss right after onConfirm (harmless when onDismiss is just "close the dialog",
    // which is all its other callers use it for), but here the two outcomes are genuinely
    // different real actions (save-only vs. save-and-open-a-log), so onConfirm firing onDismiss
    // too would save twice.
    pendingResolvedIssue?.let { resolved ->
        AlertDialog(
            onDismissRequest = { onSave(resolved); pendingResolvedIssue = null },
            title = { Text("Log how this was fixed?") },
            text = { Text("\"${resolved.title}\" is being marked resolved. Log the repair that fixed it?") },
            confirmButton = {
                TextButton(onClick = { onResolvedWithLog(resolved); pendingResolvedIssue = null }) {
                    Text("Log it", color = garageColors.ok)
                }
            },
            dismissButton = {
                TextButton(onClick = { onSave(resolved); pendingResolvedIssue = null }) { Text("Just save") }
            },
        )
    }
}
