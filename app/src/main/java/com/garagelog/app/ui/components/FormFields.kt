package com.garagelog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.isoToUtcMillis
import com.garagelog.app.util.utcMillisToIso

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, isoValue: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = if (isoValue.isBlank()) "" else formatDate(isoValue),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) { Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date") }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(isoValue))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(utcMillisToIso(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun VehicleDropdown(label: String, vehicles: List<VehicleEntity>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = vehicles.find { it.id == selectedId }?.name ?: ""

    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.85f)) {
            vehicles.forEach { v ->
                DropdownMenuItem(text = { Text(v.name) }, onClick = { onSelect(v.id); expanded = false })
            }
        }
    }
}
