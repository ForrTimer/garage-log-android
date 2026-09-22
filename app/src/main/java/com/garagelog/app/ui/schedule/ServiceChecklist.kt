package com.garagelog.app.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.ui.components.LabeledTextField
import com.garagelog.app.ui.theme.garageColors
import com.garagelog.app.util.CommonServiceTemplate
import java.util.UUID

/**
 * One row of a "which services should this vehicle track?" checklist. Intervals are held as the
 * text being edited, not numbers, so a half-typed field doesn't snap back while typing.
 */
data class ServiceDraft(
    val name: String,
    val miles: String,
    val months: String,
    val checked: Boolean,
    /** Why this interval — Bob's one-line reasoning; null for the built-in defaults. */
    val note: String? = null,
) {
    fun toSchedule(vehicleId: String, now: Long) = MaintenanceScheduleEntity(
        id = UUID.randomUUID().toString(),
        vehicleId = vehicleId,
        taskName = name,
        intervalMiles = miles.filter(Char::isDigit).toIntOrNull(),
        intervalMonths = months.filter(Char::isDigit).toIntOrNull(),
        lastDoneMileage = null,
        lastDoneDate = null,
        updatedAt = now,
    )

    companion object {
        fun from(template: CommonServiceTemplate, checked: Boolean) = ServiceDraft(
            name = template.name,
            miles = template.intervalMiles?.toString().orEmpty(),
            months = template.intervalMonths?.toString().orEmpty(),
            checked = checked,
        )
    }
}

/**
 * Ticking a service reveals its interval fields prefilled with the default, so the default is a
 * starting point rather than something to go and fix in the Maintenance screen afterwards.
 */
@Composable
fun ServiceChecklist(
    drafts: List<ServiceDraft>,
    onChange: (List<ServiceDraft>) -> Unit,
    /** Names already tracked on this vehicle, shown but not offered again. */
    alreadyTracked: Set<String> = emptySet(),
) {
    Column {
        drafts.forEachIndexed { index, draft ->
            fun update(transform: (ServiceDraft) -> ServiceDraft) =
                onChange(drafts.toMutableList().also { it[index] = transform(draft) })

            val tracked = draft.name.lowercase() in alreadyTracked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !tracked) { update { it.copy(checked = !it.checked) } }
                    .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = draft.checked && !tracked,
                    enabled = !tracked,
                    onCheckedChange = { checked -> update { it.copy(checked = checked) } },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(draft.name, style = MaterialTheme.typography.bodyMedium)
                    val summary = when {
                        tracked -> "Already tracked"
                        !draft.checked -> intervalSummary(draft)
                        else -> null
                    }
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted) }
                }
            }
            AnimatedVisibility(visible = draft.checked && !tracked) {
                Column(modifier = Modifier.padding(start = 48.dp, bottom = 6.dp)) {
                    Row {
                        LabeledTextField(
                            "Every (miles)",
                            draft.miles,
                            { v -> update { it.copy(miles = v.filter(Char::isDigit)) } },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        LabeledTextField(
                            "Every (months)",
                            draft.months,
                            { v -> update { it.copy(months = v.filter(Char::isDigit)) } },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    draft.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = garageColors.textMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** "5,000 mi or 6 mo" — whichever limits apply; blank both means "no interval set". */
fun intervalSummary(draft: ServiceDraft): String {
    val parts = listOfNotNull(
        draft.miles.toIntOrNull()?.let { "%,d mi".format(it) },
        draft.months.toIntOrNull()?.let { "$it mo" },
    )
    return if (parts.isEmpty()) "No interval set" else "Every " + parts.joinToString(" or ")
}
