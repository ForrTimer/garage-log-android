package com.garagelog.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.formatDateTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccountSection(modifier: Modifier = Modifier, account: AccountViewModel = viewModel(factory = AccountViewModel.Factory)) {
    val activity = LocalActivity.current
    val email by account.signedInEmail.collectAsStateWithLifecycle()
    val syncStatus by account.syncStatus.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        account.completeDriveConsent(result.data)
    }
    LaunchedEffect(Unit) {
        account.pendingDriveConsent.collectLatest { intentSender ->
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
                onClick = { activity?.let { account.beginSignIn(it) } },
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
                OutlinedButton(onClick = { account.syncNow() }, modifier = Modifier.weight(1f)) { Text("Sync now") }
                OutlinedButton(onClick = { account.signOut() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
            }
        }
    }
}
