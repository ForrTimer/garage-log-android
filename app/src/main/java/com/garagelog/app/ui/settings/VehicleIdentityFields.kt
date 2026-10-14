package com.garagelog.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.catalog.CatalogConfig
import com.garagelog.app.data.catalog.CatalogSpec
import com.garagelog.app.data.catalog.VehicleCatalog
import com.garagelog.app.ui.components.ActionLink
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.components.ReadOnlyDropdownField
import com.garagelog.app.ui.components.SuggestField
import com.garagelog.app.ui.theme.garageColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun IdentityPickers(catalog: VehicleCatalog, form: VehicleFormState, onChange: (VehicleFormState) -> Unit) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val year = form.year.toIntOrNull()?.takeIf { it in 1900..2100 }
    var makes by remember { mutableStateOf(emptyList<String>()) }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var configs by remember { mutableStateOf(emptyList<CatalogConfig>()) }
    var loadingModels by remember { mutableStateOf(false) }
    var chosenConfig by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(year) {
        makes = if (year == null) emptyList() else catalog.makes(year)
    }
    LaunchedEffect(year, form.make) {
        models = emptyList()
        if (year == null || form.make.isBlank()) return@LaunchedEffect
        delay(350)
        loadingModels = true
        models = catalog.models(year, form.make)
        loadingModels = false
    }
    LaunchedEffect(year, form.make, form.model) {
        configs = emptyList()
        if (year == null || form.make.isBlank() || form.model.isBlank()) return@LaunchedEffect
        delay(350)
        configs = catalog.configurations(year, form.make, form.model)
    }

    SuggestField(
        "Year",
        form.year,
        { onChange(form.copy(year = it.filter(Char::isDigit).take(4))) },
        catalog.years().map { it.toString() },
        keyboardType = KeyboardType.Number,
    )
    SuggestField(
        "Make",
        form.make,
        { onChange(form.copy(make = it)) },
        makes,
        supportingText = if (year == null) "Pick a year first to narrow the list." else null,
    )
    SuggestField(
        "Model",
        form.model,
        { onChange(form.copy(model = it)) },
        models,
        loading = loadingModels,
    )
    if (configs.isNotEmpty()) {
        ReadOnlyDropdownField(
            displayValue = chosenConfig ?: "",
            options = configs,
            optionLabel = { it.label },
            onSelect = { config ->
                chosenConfig = config.label
                focusManager.clearFocus()
                scope.launch {
                    catalog.configurationSpec(config.id)?.let { spec ->
                        // Keep a more specific typed model ("Tacoma TRD") over the EPA base name.
                        val keepModel = spec.model != null && form.model.startsWith(spec.model, ignoreCase = true)
                        onChange(form.apply(if (keepModel) spec.copy(model = null) else spec))
                    }
                }
            },
            label = "Engine / transmission",
        )
        Text(
            "Pick one to fill in the engine, transmission and drivetrain below.",
            style = MaterialTheme.typography.bodySmall,
            color = garageColors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun VinLookup(
    catalog: VehicleCatalog,
    vin: String,
    onVinChange: (String) -> Unit,
    onDecoded: (CatalogSpec) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var decoding by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LabeledTextField(
            "VIN",
            vin,
            { onVinChange(it.uppercase().filter(Char::isLetterOrDigit).take(17)); status = null },
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.padding(top = 10.dp), contentAlignment = Alignment.Center) {
            if (decoding) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                ActionLink("Decode", onClick = {
                    if (vin.length != 17) {
                        status = "A VIN is 17 characters — this one is ${vin.length}."
                        return@ActionLink
                    }
                    decoding = true
                    scope.launch {
                        val spec = catalog.decodeVin(vin)
                        decoding = false
                        status = if (spec == null) {
                            "Couldn't decode that VIN (offline, or NHTSA doesn't know it). Fill it in below."
                        } else {
                            onDecoded(spec)
                            "Filled in from NHTSA. Check it over; older vehicles often decode partly."
                        }
                    }
                })
            }
        }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted, modifier = Modifier.padding(top = 4.dp)) }
}

