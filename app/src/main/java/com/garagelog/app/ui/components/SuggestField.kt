package com.garagelog.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A text field whose dropdown narrows to [suggestions] matching what's typed, while still taking
 * any value — a picker for the common case that never blocks the uncommon one (an engine swap, a
 * model neither catalog lists, a trim nobody's heard of).
 *
 * Matches anywhere in the option, but options that *start* with the typed text sort first, so
 * "sil" puts "Silverado" above "Sierra Silverado Edition".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onSuggestionPicked: (String) -> Unit = onValueChange,
    loading: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Held as a TextFieldValue so a value set from outside (a picked suggestion, a VIN decode)
    // puts the cursor at the end. With the plain String overload the cursor stays where typing
    // stopped, so picking "Toyota" after typing "Toy" left it mid-word and the next keystroke
    // landed inside the name.
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val fieldValue = if (editing.text == value) editing else TextFieldValue(value, TextRange(value.length))
    val typed = value.trim()
    val filtered = if (typed.isEmpty()) {
        suggestions
    } else {
        suggestions
            .filter { it.contains(typed, ignoreCase = true) }
            .sortedByDescending { it.startsWith(typed, ignoreCase = true) }
    }.take(MAX_SHOWN)
    // Nothing left to suggest once the field holds exactly the one match.
    val showMenu = expanded && filtered.isNotEmpty() && !(filtered.size == 1 && filtered[0].equals(typed, true))

    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                editing = it
                if (it.text != value) {
                    onValueChange(it.text)
                    expanded = true
                }
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                }
            },
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSuggestionPicked(option)
                        expanded = false
                        // On to the next field: year → make → model reads as one flow.
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Enough to scroll through a make's model list; beyond this, typing narrows faster than scrolling. */
private const val MAX_SHOWN = 60
