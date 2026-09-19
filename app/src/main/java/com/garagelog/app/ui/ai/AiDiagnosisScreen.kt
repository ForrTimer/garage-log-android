package com.garagelog.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.ai.ClaudeSource
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.ui.AiStreamState
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.MarkdownText
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AiDiagnosisScreen(
    issue: IssueEntity,
    vehicleName: String,
    diagnosis: AiDiagnosisEntity?,
    /**
     * Taken as a flow and collected here on purpose: it changes on every streamed token, so
     * collecting it in the caller would recompose the whole app tree dozens of times per answer.
     */
    streamFlow: StateFlow<AiStreamState>,
    hasApiKey: Boolean,
    sourcesFor: (String) -> List<ClaudeSource>,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val stream by streamFlow.collectAsState()
    val error = stream.error
    val scrollState = rememberScrollState()

    // Follow the answer as it streams, but only while it's streaming — once it's done the reader
    // owns the scroll position.
    LaunchedEffect(stream.partialText) {
        if (stream.running && stream.partialText.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Diagnosis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (stream.running) ActionLink(text = "Stop", onClick = onStop)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(GarageDimens.subScreenContentPadding),
        ) {
            GarageCard(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "$vehicleName · opened ${formatDate(issue.dateOpened)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                !hasApiKey -> GarageCard(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Claude isn't set up yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add an Anthropic API key to have Claude work through this issue using this " +
                            "vehicle's service history and research whether it's a known problem.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                    )
                    ActionLink(text = "Set up Claude", onClick = onOpenSettings)
                }

                error != null -> GarageCard(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Couldn't get a diagnosis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    ActionLink(text = "Try again", onClick = onRun)
                }

                stream.running -> Column {
                    RunningIndicator(stream.searching)
                    if (stream.partialText.isNotEmpty()) MarkdownText(stream.partialText)
                }

                diagnosis != null -> Column {
                    MarkdownText(diagnosis.content)
                    SourcesSection(sourcesFor(diagnosis.sourcesJson))
                    Text(
                        text = buildString {
                            append("${diagnosis.model} · ${formatTimestamp(diagnosis.createdAt)}")
                            diagnosis.milesAtRun?.let { append(" · at ${formatMiles(it)}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Row(modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)) {
                        ActionLink(text = "Run again", onClick = onRun)
                    }
                }

                else -> Column {
                    EmptyState(
                        "Claude will work through this issue using this vehicle's real service " +
                            "history and search the web for whether it's a known problem on this " +
                            "year, make and model.",
                        icon = Icons.Filled.AutoAwesome,
                    )
                    Button(onClick = onRun, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                        Text("Diagnose this issue")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningIndicator(searching: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        Text(
            if (searching) "Searching the web…" else "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SourcesSection(sources: List<ClaudeSource>) {
    if (sources.isEmpty()) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            "Sources",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            MarkdownText("- [${source.title}](${source.url})")
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(millis))
