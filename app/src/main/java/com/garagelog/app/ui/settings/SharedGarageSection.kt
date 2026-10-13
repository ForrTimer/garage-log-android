package com.garagelog.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.cloud.GarageCloud
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.garageColors

/**
 * Shared garage (Firebase): set up, and what's there. For now this only creates the owner's garage
 * and uploads this phone's records; inviting people and two-way sync come in later steps.
 */
@Composable
fun SharedGarageSection(viewModel: GarageLogViewModel, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val membership by viewModel.garageMembership.collectAsState()
    val running by viewModel.garageSetupRunning.collectAsState()
    val lastSetup by viewModel.lastGarageSetup.collectAsState()

    GarageCard(modifier = modifier) {
        Text("Shared garage", style = MaterialTheme.typography.titleMedium)
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
        if (membership.garageId == null) {
            Text(
                "Keep your vehicles in a shared garage so other people can log fuel, service and issues on the ones you give them.",
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

        Button(
            onClick = { activity?.let { viewModel.setUpSharedGarage(it) } },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(
                when {
                    running -> "Setting up…"
                    membership.garageId == null -> "Set up shared garage"
                    else -> "Reconnect"
                },
            )
        }
    }
}
