package com.garagelog.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.formatDateTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccountSection(viewModel: GarageLogViewModel, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val email by viewModel.signedInEmail.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.completeDriveConsent(result.data)
    }
    LaunchedEffect(Unit) {
        viewModel.pendingDriveConsent.collectLatest { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    GarageCard(modifier = modifier) {
        Text("Account & sync", style = MaterialTheme.typography.titleMedium)

        if (email == null) {
            Text(
                "Sign in with Google to sync your data and photos to Drive automatically, across every device you're signed into.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { activity?.let { viewModel.beginSignIn(it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) { Text("Sign in with Google") }
        } else {
            Text(email ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    syncStatus.isSyncing -> "Syncing…"
                    syncStatus.lastError != null -> "Last sync failed: ${syncStatus.lastError}"
                    else -> "Last synced: ${formatDateTime(syncStatus.lastSyncedAt)}"
                },
                color = if (syncStatus.lastError != null) garageColors.alarmText else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                OutlinedButton(onClick = { viewModel.syncNow() }, modifier = Modifier.weight(1f)) { Text("Sync now") }
                OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
            }
        }
    }
}
