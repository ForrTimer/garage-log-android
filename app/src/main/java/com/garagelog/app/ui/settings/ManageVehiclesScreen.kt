package com.garagelog.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.components.EmptyState
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.GarageDimens

/** Vehicle roster, split out of Settings so it's one tap from the drawer instead of a scroll. */
@Composable
fun ManageVehiclesScreen(
    vehicles: List<VehicleEntity>,
    onAddVehicle: () -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onReorderVehicles: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(contentPadding = GarageDimens.subScreenContentPadding) {
            item {
                GarageCard(modifier = Modifier.padding(top = 8.dp)) {
                    if (vehicles.isEmpty()) {
                        EmptyState("No vehicles yet.", icon = Icons.Filled.DirectionsCar)
                    } else {
                        vehicles.forEachIndexed { index, v ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable { onEditVehicle(v) }.padding(vertical = 10.dp),
                            ) {
                                Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        v.year?.toString(),
                                        v.make.ifBlank { null },
                                        v.model.ifBlank { null },
                                    ).joinToString(" "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (index != vehicles.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        OutlinedButton(onClick = onAddVehicle, modifier = Modifier.weight(1f)) { Text("+ Add vehicle") }
                        if (vehicles.size > 1) {
                            OutlinedButton(onClick = onReorderVehicles, modifier = Modifier.weight(1f)) {
                                Text("Reorder")
                            }
                        }
                    }
                }
            }
        }
    }
}
