package com.garagelog.app.ui.issues

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    FormSheetScaffold(
        title = if (issue == null) "New issue" else "Edit issue",
        onDismiss = onDismiss,
        showDelete = issue != null,
        deleteTitle = "Delete issue?",
        onDelete = { issue?.let { onDelete(it.id) } },
        onSave = {
            onSave(
                IssueEntity(
                    id = issue?.id ?: UUID.randomUUID().toString(),
                    vehicleId = form.vehicleId,
                    title = form.title.trim().ifBlank { "Untitled issue" },
                    status = form.status,
                    priority = form.priority,
                    dateOpened = form.dateOpened.ifBlank { todayIso() },
                    dateResolved = form.dateResolved,
                    description = form.description.trim(),
                ),
            )
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
        )

        DateField("Date opened", form.dateOpened) { form = form.copy(dateOpened = it) }
        LabeledTextField("Description / diagnosis notes", form.description, { form = form.copy(description = it) }, singleLine = false, minLines = 3)

        if (issue != null) {
            PhotoGridSection(viewModel = viewModel, ownerType = PhotoOwnerType.ISSUE, ownerId = issue.id)
        }
    }
}
