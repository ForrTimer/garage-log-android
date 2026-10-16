package com.garagelog.app.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.ai.HowToVideo
import com.garagelog.app.data.ai.howToKey
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.ai.BobViewModel
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.howToSearchUrl

/**
 * How to do [task] on [vehicle]: a free YouTube search for everyone, and — when [canAskBob] —
 * Bob's pick of the single best video, which costs a few cents of the owner's Bob credit.
 */
@Composable
fun HowToDialog(
    vehicle: VehicleEntity,
    task: String,
    canAskBob: Boolean,
    onDismiss: () -> Unit,
    bob: BobViewModel = viewModel(factory = BobViewModel.Factory),
) {
    val uriHandler = LocalUriHandler.current
    val hasKey by bob.hasApiKey.collectAsStateWithLifecycle()
    val runs by bob.runs.collectAsStateWithLifecycle()
    val run = runs[howToKey(vehicle.id, task)]
    var video by remember(vehicle.id, task) { mutableStateOf<HowToVideo?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How-to: $task") },
        text = {
            Column {
                Text(
                    listOfNotNull(vehicle.year?.toString(), vehicle.make, vehicle.model).filter { it.isNotBlank() }.joinToString(" "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { uriHandler.openUri(howToSearchUrl(vehicle, task)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Search YouTube") }

                if (canAskBob) {
                    val picked = video
                    when {
                        picked != null -> Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text("$ASSISTANT_NAME's pick", style = MaterialTheme.typography.labelMedium, color = garageColors.textMuted)
                            Text(picked.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
                            if (picked.channel.isNotBlank()) {
                                Text(picked.channel, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted)
                            }
                            if (picked.why.isNotBlank()) {
                                Text(picked.why, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { uriHandler.openUri(picked.url) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text("Watch") }
                        }
                        run?.running == true -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(run.statusText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                        }
                        !hasKey -> Text(
                            "Add an API key in Settings → $ASSISTANT_NAME to have $ASSISTANT_NAME pick the best video.",
                            style = MaterialTheme.typography.bodySmall,
                            color = garageColors.textMuted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        else -> Column {
                            run?.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (run.declined) MaterialTheme.colorScheme.onSurface else garageColors.alarmText,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = { bob.findHowTo(vehicle, task) { video = it } },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text("Ask $ASSISTANT_NAME for the best one") }
                            Text(
                                "One web search on your Anthropic account, usually a few cents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = garageColors.textMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
