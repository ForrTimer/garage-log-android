package com.garagelog.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.GarageCard

/**
 * API-key setup for the Claude features. The key lives in encrypted storage on this device only —
 * it is never written to the Room database, the JSON backup, or the Drive sync snapshot.
 */
@Composable
fun ClaudeAiSection(viewModel: GarageLogViewModel, modifier: Modifier = Modifier) {
    val hasKey by viewModel.hasAiKey.collectAsState()
    var draft by remember { mutableStateOf("") }
    var confirmingRemove by remember { mutableStateOf(false) }

    if (confirmingRemove) {
        ConfirmDialog(
            title = "Remove API key?",
            message = "Diagnoses and chats already saved stay on this device, but you won't be able to run new ones until you add a key again.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.clearAiApiKey() },
            onDismiss = { confirmingRemove = false },
        )
    }

    GarageCard(modifier = modifier) {
        Text("Claude AI", style = MaterialTheme.typography.titleMedium)

        if (hasKey) {
            Text(
                "Connected with key ${viewModel.aiKeyHint() ?: ""}. Diagnose issues from the Issues tab, " +
                    "or ask about a vehicle from its card on Home.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                OutlinedButton(onClick = { confirmingRemove = true }, modifier = Modifier.weight(1f)) {
                    Text("Remove key")
                }
            }
        } else {
            Text(
                "Paste an Anthropic API key to turn on issue diagnosis and per-vehicle Q&A. Create one " +
                    "at console.anthropic.com. Calls are billed to your own Anthropic account — roughly " +
                    "a few cents to about 20 cents per answer, since each one can search the web.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Button(
                onClick = {
                    viewModel.setAiApiKey(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Save key")
            }
            Text(
                "Stored encrypted on this device only — never in your backup file or Drive sync.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
