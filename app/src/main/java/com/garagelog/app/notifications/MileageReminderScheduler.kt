package com.garagelog.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.entity.ReminderCadence
import java.util.Calendar

const val NOTIFICATION_CHANNEL_ID = "mileage_reminders"

private const val REQUEST_CODE = 4200

/**
 * Schedules (or cancels) the recurring "log your mileage" reminder via AlarmManager. Uses
 * `setAndAllowWhileIdle` rather than an exact alarm — a reminder landing a little late under
 * Doze is an acceptable trade for not requiring the special SCHEDULE_EXACT_ALARM permission.
 * Each firing (see [MileageReminderReceiver]) reschedules the next one — alarms are one-shot.
 */
object MileageReminderScheduler {
    fun reschedule(context: Context, prefs: NotificationPrefsEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        if (!prefs.enabled) return
        val triggerAt = nextTriggerMillis(prefs)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MileageReminderReceiver::class.java)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Next wall-clock instant matching [prefs]'s cadence/time-of-interval that is strictly after now. */
    fun nextTriggerMillis(prefs: NotificationPrefsEntity, now: Calendar = Calendar.getInstance()): Long {
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, prefs.hour)
            set(Calendar.MINUTE, prefs.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (ReminderCadence.valueOf(prefs.cadence)) {
            ReminderCadence.Daily -> {
                if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            ReminderCadence.Weekly -> {
                candidate.set(Calendar.DAY_OF_WEEK, prefs.dayOfWeek)
                if (!candidate.after(now)) candidate.add(Calendar.WEEK_OF_YEAR, 1)
            }
            ReminderCadence.Monthly -> {
                setDayOfMonthClamped(candidate, prefs.dayOfMonth)
                if (!candidate.after(now)) {
                    candidate.set(Calendar.DAY_OF_MONTH, 1) // safe value before changing MONTH
                    candidate.add(Calendar.MONTH, 1)
                    setDayOfMonthClamped(candidate, prefs.dayOfMonth)
                }
            }
            ReminderCadence.Yearly -> {
                candidate.set(Calendar.DAY_OF_MONTH, 1) // safe value before changing MONTH
                candidate.set(Calendar.MONTH, prefs.month)
                setDayOfMonthClamped(candidate, prefs.dayOfMonth)
                if (!candidate.after(now)) {
                    candidate.set(Calendar.DAY_OF_MONTH, 1)
                    candidate.add(Calendar.YEAR, 1)
                    setDayOfMonthClamped(candidate, prefs.dayOfMonth)
                }
            }
        }
        return candidate.timeInMillis
    }

    /**
     * Sets DAY_OF_MONTH to [day], clamped to the current YEAR/MONTH's actual last day —
     * Calendar.set(DAY_OF_MONTH, 31) on a 30-day month rolls into next month instead of
     * clamping, which isn't what "the 31st of every month" should mean.
     */
    private fun setDayOfMonthClamped(cal: Calendar, day: Int) {
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
    }
}
