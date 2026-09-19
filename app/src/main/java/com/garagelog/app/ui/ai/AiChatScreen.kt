package com.garagelog.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.ai.ClaudeSource
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.ui.AiStreamState
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.MarkdownText
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AiChatScreen(
    vehicleLabel: String,
    messages: List<AiChatMessageEntity>,
    /** Collected here, not by the caller — it changes on every streamed token. */
    streamFlow: StateFlow<AiStreamState>,
    hasApiKey: Boolean,
    sourcesFor: (String) -> List<ClaudeSource>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val stream by streamFlow.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmingClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Keep the newest turn in view as it streams and as new turns land.
    LaunchedEffect(messages.size, stream.partialText, stream.running) {
        val lastIndex = messages.size // +1 for the streaming/error row when present
        if (lastIndex >= 0) listState.animateScrollToItem(maxOf(0, lastIndex))
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = "Clear this conversation?",
            message = "The whole chat history for $vehicleLabel will be deleted. This can't be undone.",
            confirmLabel = "Clear",
            onConfirm = onClear,
            onDismiss = { confirmingClear = false },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask Claude", style = MaterialTheme.typography.titleMedium)
                Text(
                    vehicleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                stream.running -> ActionLink(text = "Stop", onClick = onStop)
                messages.isNotEmpty() -> ActionLink(text = "Clear", onClick = { confirmingClear = true })
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (messages.isEmpty() && !stream.running) {
                item {
                    EmptyState(
                        if (hasApiKey) {
                            "Ask anything about $vehicleLabel — fluid specs, torque values, what a " +
                                "noise might be, whether a service is worth doing. Claude can see this " +
                                "vehicle's full service history."
                        } else {
                            "Add an Anthropic API key to start asking questions about your vehicles."
                        },
                        icon = Icons.Filled.AutoAwesome,
                    )
                    if (!hasApiKey) {
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            ActionLink(text = "Set up Claude", onClick = onOpenSettings)
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message, sources = sourcesFor(message.sourcesJson))
            }

            val error = stream.error
            if (stream.running || error != null) {
                item {
                    when {
                        error != null -> Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        stream.partialText.isNotEmpty() -> MarkdownText(stream.partialText)
                        else -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                            Text(
                                if (stream.searching) "Searching the web…" else "Thinking…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Ask about this vehicle…") },
                enabled = hasApiKey,
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = hasApiKey && draft.isNotBlank() && !stream.running,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiChatMessageEntity, sources: List<ClaudeSource>) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            MarkdownText(message.content)
            SourcesSection(sources)
        }
    }
}
