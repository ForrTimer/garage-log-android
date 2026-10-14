package com.garagelog.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
import com.garagelog.app.GarageLogApplication
import com.garagelog.app.MainActivity
import com.garagelog.app.ui.theme.DarkAlarm
import com.garagelog.app.ui.theme.DarkOk
import com.garagelog.app.ui.theme.DarkPanel
import com.garagelog.app.ui.theme.DarkText
import com.garagelog.app.ui.theme.DarkTextMuted
import com.garagelog.app.ui.theme.DarkWarn
import com.garagelog.app.ui.theme.LightAlarm
import com.garagelog.app.ui.theme.LightOk
import com.garagelog.app.ui.theme.LightPanel
import com.garagelog.app.ui.theme.LightText
import com.garagelog.app.ui.theme.LightTextMuted
import com.garagelog.app.ui.theme.LightWarn
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo

private data class WidgetCandidate(val vehicleName: String, val taskName: String, val label: String, val status: DueStatus)

/**
 * Home-screen widget showing the single most urgent maintenance item across every vehicle —
 * the one thing an owner actually wants to see without opening the app. Tapping it opens the app.
 * Refreshed by ServiceLocator.requestSync() after every data mutation (see its call site), with
 * the platform's own updatePeriodMillis as a once-per-30-min safety net.
 */
class GarageLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locator = (context.applicationContext as GarageLogApplication).serviceLocator
        val vehicles = locator.vehicleRepository.getAll()
        val schedules = locator.scheduleRepository.getAll()

        val candidates = vehicles.flatMap { v ->
            schedules.filter { it.vehicleId == v.id }.map { s ->
                val info = computeDueInfo(s, v.miles, v.isSevereDuty)
                WidgetCandidate(v.name, s.taskName, info.label, info.status)
            }
        }
        val next = candidates.firstOrNull { it.status == DueStatus.OVERDUE }
            ?: candidates.firstOrNull { it.status == DueStatus.DUE_SOON }

        val openAppIntent = Intent(context, MainActivity::class.java)
        provideContent {
            WidgetContent(next, openAppIntent)
        }
    }
}

@Composable
private fun WidgetContent(next: WidgetCandidate?, openAppIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(16.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openAppIntent)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "GARAGE LOG",
            style = TextStyle(color = WidgetColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (next == null) {
            Text(
                text = "✓ All maintenance on track",
                style = TextStyle(color = WidgetColors.ok, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        } else {
            val statusColor = if (next.status == DueStatus.OVERDUE) WidgetColors.overdue else WidgetColors.dueSoon
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    text = if (next.status == DueStatus.OVERDUE) "OVERDUE" else "DUE SOON",
                    style = TextStyle(color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "${next.vehicleName} — ${next.taskName}",
                style = TextStyle(color = WidgetColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = next.label,
                style = TextStyle(color = WidgetColors.textMuted, fontSize = 13.sp),
                maxLines = 2,
            )
        }
    }
}

/**
 * The app theme's own colors, day and night. Glance draws in the launcher's process, so it can't
 * read GarageColors from a composition local, but it can take the same values as day/night pairs.
 */
private object WidgetColors {
    val background: GlanceColor = ColorProvider(day = LightPanel, night = DarkPanel)
    val text: GlanceColor = ColorProvider(day = LightText, night = DarkText)
    val textMuted: GlanceColor = ColorProvider(day = LightTextMuted, night = DarkTextMuted)
    val overdue: GlanceColor = ColorProvider(day = LightAlarm, night = DarkAlarm)
    val dueSoon: GlanceColor = ColorProvider(day = LightWarn, night = DarkWarn)
    val ok: GlanceColor = ColorProvider(day = LightOk, night = DarkOk)
}
