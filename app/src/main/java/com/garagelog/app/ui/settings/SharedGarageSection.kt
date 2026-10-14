package com.garagelog.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.cloud.GarageCloud
import com.garagelog.app.data.cloud.GarageSetupOutcome
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.garageColors

/**
 * Shared garage (Firebase): set up or join, what role this phone has, and for admins the way in to
 * members and permissions.
 */
@Composable
fun SharedGarageSection(viewModel: GarageLogViewModel, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val membership by viewModel.garageMembership.collectAsState()
    val running by viewModel.garageSetupRunning.collectAsState()
    val lastSetup by viewModel.lastGarageSetup.collectAsState()
    val pendingInvite by viewModel.pendingInvite.collectAsState()

    GarageCard(modifier = modifier) {
        Text("Shared garage", style = MaterialTheme.typography.titleMedium)
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
        if (membership.garageId == null) {
            Text(
                "Keep your vehicles in a shared garage so other people can log fuel, service and issues on the ones you give them. If someone invited you, this is where you join.",
                color = secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val role = if (membership.role == GarageCloud.ROLE_ADMIN) "Admin" else "Standard member"
            Text("$role · ${membership.email.orEmpty()}", color = secondary, style = MaterialTheme.typography.bodyMedium)
        }

        lastSetup?.takeIf { it.uploaded }?.let { result ->
            val lines = result.local.keys.joinToString("\n") { kind ->
                val local = result.local[kind] ?: 0
                val cloud = result.cloud[kind] ?: 0
                "${kind.replaceFirstChar { it.uppercase() }}: $local on this phone, $cloud in the garage"
            }
            Text(
                lines,
                color = if (result.matches) secondary else garageColors.alarmText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (membership.isAdmin) {
            Button(onClick = viewModel::openMembers, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Members & permissions")
            }
        }
        OutlinedButton(
            onClick = { activity?.let { viewModel.setUpSharedGarage(it) } },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(top = if (membership.isAdmin) 6.dp else 10.dp),
        ) {
            Text(
                when {
                    running -> "Working…"
                    membership.garageId == null -> "Set up or join a shared garage"
                    else -> "Reconnect"
                },
            )
        }
    }

    pendingInvite?.let { JoinDialog(it, viewModel) }
}

/**
 * The answer to an invitation. An admin's vehicles merge into the garage; a Standard member's
 * can't (they don't get to add vehicles to someone else's garage), so they're offered a backup
 * before the phone switches over to only what they were given.
 */
@Composable
private fun JoinDialog(pending: GarageSetupOutcome.Invited, viewModel: GarageLogViewModel) {
    val context = LocalContext.current
    val invite = pending.invite
    val asAdmin = invite.role == GarageCloud.ROLE_ADMIN
    val garage = invite.garageName.ifBlank { "a shared garage" }
    val from = invite.invitedByName.ifBlank { "Someone" }
    val count = pending.localVehicleCount
    val vehicles = if (count == 1) "1 vehicle" else "$count vehicles"

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val stream = uri?.let { context.contentResolver.openOutputStream(it) }
        if (stream != null) viewModel.acceptInvite(clearLocal = true, backupTo = stream)
    }

    AlertDialog(
        onDismissRequest = viewModel::dismissInvite,
        title = { Text("Join $garage?") },
        text = {
            Column {
                Text("$from invited you as ${if (asAdmin) "an admin" else "a Standard member"}.")
                if (count > 0) {
                    Text(
                        if (asAdmin) {
                            "The $vehicles on this phone will be added to the shared garage."
                        } else {
                            "This phone will show only the vehicles shared with you. Its own $vehicles and their history will be removed from it; back them up first if you want to keep them."
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Column {
                if (!asAdmin && count > 0) {
                    TextButton(onClick = { backupLauncher.launch("garage-log-backup.json") }) { Text("Back up, then join") }
                    TextButton(onClick = { viewModel.acceptInvite(clearLocal = true) }) {
                        Text("Join without a backup", color = garageColors.alarmText)
                    }
                } else {
                    TextButton(onClick = { viewModel.acceptInvite(clearLocal = !asAdmin) }) { Text("Join") }
                }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissInvite) { Text("Not now") } },
    )
}
