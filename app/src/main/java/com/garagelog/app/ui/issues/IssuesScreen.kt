package com.garagelog.app.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssuePriority
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.ui.GarageLogUiState
import com.garagelog.app.ui.components.ChipFilterRow
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SearchField
import com.garagelog.app.ui.components.SwipeToDeleteRow
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.util.formatDate

private fun statusTone(status: String): PillTone = when (status) {
    IssueStatus.Resolved.label -> PillTone.Resolved
    IssueStatus.InProgress.label -> PillTone.Progress
    else -> PillTone.Open
}

private fun statusRank(status: String): Int = when (status) {
    IssueStatus.Resolved.label -> 2
    IssueStatus.InProgress.label -> 1
    else -> 0
}

@Composable
fun IssuesScreen(uiState: GarageLogUiState, onItemClick: (IssueEntity) -> Unit, onDelete: (IssueEntity) -> Unit) {
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val issues = uiState.issuesFor(uiState.activeVehicleId)
        .filter { statusFilter == null || it.status == statusFilter }
        .filter { issue ->
            query.isBlank() ||
                issue.title.contains(query, ignoreCase = true) ||
                issue.description.contains(query, ignoreCase = true)
        }
        .sortedWith(compareBy<IssueEntity> { statusRank(it.status) }.thenByDescending { it.dateOpened })

    LazyColumn(contentPadding = GarageDimens.listContentPaddingWithFab) {
        item {
            SearchField(query, onQueryChange = { query = it }, placeholder = "Search issues…")
            ChipFilterRow(
                options = IssueStatus.entries.map { it.label },
                selected = statusFilter,
                onSelect = { statusFilter = it },
            )
        }
        item {
            GarageCard(modifier = Modifier.padding(top = 12.dp)) {
                if (issues.isEmpty()) {
                    EmptyState(
                        when {
                            query.isNotBlank() -> "No issues match \"$query\"."
                            statusFilter != null -> "No $statusFilter issues."
                            else -> "No issues logged. Tap + to add a gremlin or open item."
                        },
                        icon = Icons.Filled.Warning,
                    )
                } else {
                    issues.forEachIndexed { index, issue ->
                        IssueRow(issue, uiState, onClick = { onItemClick(issue) }, onDelete = { onDelete(issue) })
                        if (index != issues.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: IssueEntity, uiState: GarageLogUiState, onClick: () -> Unit, onDelete: () -> Unit) {
    SwipeToDeleteRow(deleteTitle = "Delete this issue?", onDelete = onDelete) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PillBadge(text = issue.status, tone = statusTone(issue.status))
                }
                val subtitleParts = buildList {
                    if (uiState.activeVehicleId == null) add(uiState.vehicleName(issue.vehicleId))
                    if (issue.priority == IssuePriority.SafetyCritical.label) add("⚠ Safety-critical")
                    add("opened ${formatDate(issue.dateOpened)}")
                }
                Text(subtitleParts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
