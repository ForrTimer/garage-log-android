package com.garagelog.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.data.cloud.GarageCloud
import com.garagelog.app.data.cloud.GarageMember
import com.garagelog.app.data.cloud.StandardPermissions
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.PillBadge
import com.garagelog.app.ui.components.PillTone
import com.garagelog.app.ui.components.SegmentedControl
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.ui.theme.garageSwitchColors

private const val ADMIN = "Admin"
private const val STANDARD = "Standard"

/** Admins only: who's in the shared garage, who's invited, and what Standard members may do. */
@Composable
fun MembersScreen(
    vehicles: List<VehicleEntity>,
    garage: SharedGarageViewModel = viewModel(factory = SharedGarageViewModel.Factory),
) {
    val page by garage.membersPage.collectAsStateWithLifecycle()
    val busy by garage.membersBusy.collectAsStateWithLifecycle()
    var inviting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GarageMember?>(null) }

    LaunchedEffect(Unit) { garage.loadMembers() }

    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    fun vehicleNames(ids: List<String>) =
        ids.mapNotNull { id -> vehicles.find { it.id == id }?.name }.joinToString(", ").ifBlank { "No vehicles yet" }

    LazyColumn(contentPadding = GarageDimens.subScreenContentPadding) {
        val current = page
        if (current == null) {
            item { Text(if (busy) "Loading…" else "Couldn't load the garage.", color = secondary, modifier = Modifier.padding(top = 12.dp)) }
            return@LazyColumn
        }

        item {
            GarageCard(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Text("Members", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Admins see and change everything. Standard members see only the vehicles you give them.",
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                current.members.forEach { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !member.isOwner) { editing = member }
                            .padding(vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName.ifBlank { member.email }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    member.isOwner -> "Owner · ${member.email}"
                                    member.role == GarageCloud.ROLE_ADMIN -> member.email
                                    else -> "${member.email}\n${vehicleNames(member.vehicleIds)}"
                                },
                                color = secondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PillBadge(
                            if (member.role == GarageCloud.ROLE_ADMIN) ADMIN else STANDARD,
                            if (member.role == GarageCloud.ROLE_ADMIN) PillTone.Info else PillTone.Neutral,
                        )
                    }
                }
                Button(onClick = { inviting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Invite someone")
                }
            }
        }

        if (current.invites.isNotEmpty()) {
            item {
                GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text("Waiting to join", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "They join when they sign in to the shared garage with this email.",
                        color = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    current.invites.forEach { invite ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invite.email, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (invite.role == GarageCloud.ROLE_ADMIN) ADMIN else "$STANDARD · ${vehicleNames(invite.vehicleIds)}",
                                    color = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            ActionLink("Withdraw", onClick = { garage.withdrawInvite(invite.email) }, tint = garageColors.alarmText)
                        }
                    }
                }
            }
        }

        item {
            PermissionsCard(current.permissions, enabled = !busy, onChange = garage::saveStandardPermissions)
        }
    }

    if (inviting) {
        MemberDialog(
            title = "Invite someone",
            vehicles = vehicles,
            askEmail = true,
            initialRole = GarageCloud.ROLE_STANDARD,
            initialVehicleIds = emptyList(),
            onDismiss = { inviting = false },
            onSave = { email, role, ids -> garage.inviteMember(email, role, ids); inviting = false },
        )
    }
    editing?.let { member ->
        MemberDialog(
            title = member.displayName.ifBlank { member.email },
            vehicles = vehicles,
            askEmail = false,
            initialRole = member.role,
            initialVehicleIds = member.vehicleIds,
            onDismiss = { editing = null },
            onSave = { _, role, ids -> garage.updateMember(member.uid, role, ids); editing = null },
            onRemove = { garage.removeMember(member.uid); editing = null },
        )
    }
}

@Composable
private fun PermissionsCard(permissions: StandardPermissions, enabled: Boolean, onChange: (StandardPermissions) -> Unit) {
    GarageCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Standard members can…", style = MaterialTheme.typography.titleMedium)
        Text(
            "They can always log fuel, mileage and service, report issues, and edit what they added. Bob chat stays off for them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        PermissionRow("Edit entries other people added", permissions.editOthersEntries, enabled) {
            onChange(permissions.copy(editOthersEntries = it))
        }
        PermissionRow("Delete log entries and issues", permissions.deleteEntries, enabled) {
            onChange(permissions.copy(deleteEntries = it))
        }
        PermissionRow("Add, edit and delete maintenance schedules", permissions.editSchedules, enabled) {
            onChange(permissions.copy(editSchedules = it))
        }
        PermissionRow("Edit vehicle details", permissions.editVehicles, enabled) {
            onChange(permissions.copy(editVehicles = it))
        }
    }
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = garageSwitchColors())
    }
}

/** Invite (with [askEmail]) or edit a member: role, and for Standard, which vehicles. */
@Composable
private fun MemberDialog(
    title: String,
    vehicles: List<VehicleEntity>,
    askEmail: Boolean,
    initialRole: String,
    initialVehicleIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (email: String, role: String, vehicleIds: List<String>) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(initialRole) }
    var selected by remember { mutableStateOf(initialVehicleIds.toSet()) }
    var confirmingRemove by remember { mutableStateOf(false) }
    val standard = role == GarageCloud.ROLE_STANDARD
    val canSave = (!askEmail || email.contains('@')) && (!standard || selected.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (askEmail) {
                    LabeledTextField("Their Google account email", email, { email = it.trim() }, keyboardType = KeyboardType.Email)
                }
                SegmentedControl(
                    options = listOf(STANDARD, ADMIN),
                    selected = if (standard) STANDARD else ADMIN,
                    onSelect = { role = if (it == ADMIN) GarageCloud.ROLE_ADMIN else GarageCloud.ROLE_STANDARD },
                )
                if (standard) {
                    Text(
                        "Vehicles they can see",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    vehicles.forEach { vehicle ->
                        val checked = vehicle.id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = if (checked) selected - vehicle.id else selected + vehicle.id
                            },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(vehicle.name, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                } else {
                    Text(
                        "Admins see every vehicle and can change anything, including members.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (onRemove != null) {
                    ActionLink(
                        "Remove from garage",
                        onClick = { confirmingRemove = true },
                        tint = garageColors.alarmText,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(email, role, selected.toList()) }, enabled = canSave) {
                Text(if (askEmail) "Invite" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmingRemove && onRemove != null) {
        ConfirmDialog(
            title = "Remove $title?",
            message = "They lose access on their next sync. What they logged stays in the garage.",
            confirmLabel = "Remove",
            onConfirm = onRemove,
            onDismiss = { confirmingRemove = false },
        )
    }
}
