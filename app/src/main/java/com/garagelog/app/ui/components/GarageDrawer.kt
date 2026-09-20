package com.garagelog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.ui.theme.garageColors

/**
 * The left slide-out panel. Holds the destinations that aren't day-to-day enough to earn a bottom
 * tab but are too important to bury — plus Settings, which is everything that isn't a destination
 * at all.
 */
@Composable
fun GarageDrawer(
    onOpenSchedule: () -> Unit,
    onOpenManageVehicles: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = garageColors.chrome) {
        Text(
            "Garage Log",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            DrawerLink("Maintenance schedule", Icons.Filled.Build) {
                onOpenSchedule()
                onClose()
            }
            DrawerLink("Manage vehicles", Icons.Filled.DirectionsCar) {
                onOpenManageVehicles()
                onClose()
            }
            DrawerLink("Settings", Icons.Filled.Settings) {
                onOpenSettings()
                onClose()
            }
        }
    }
}

@Composable
private fun DrawerLink(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
