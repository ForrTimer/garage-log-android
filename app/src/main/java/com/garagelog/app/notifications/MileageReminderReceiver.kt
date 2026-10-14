package com.garagelog.app.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.garagelog.app.GarageLogApplication
import com.garagelog.app.MainActivity
import com.garagelog.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires the "log your mileage" notification, then immediately reschedules the next occurrence. */
class MileageReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(context)

        val pendingResult = goAsync()
        val app = context.applicationContext as GarageLogApplication
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val prefs = app.serviceLocator.notificationPrefsRepository.get()
                if (prefs.enabled) MileageReminderScheduler.reschedule(context, prefs)
            }
            pendingResult.finish()
        }
    }

    // canPostNotifications() runs first; lint can't see the check through the helper.
    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context) {
        if (!canPostNotifications(context)) return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context, 0, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle("Time to log your mileage")
            .setContentText("Keep your maintenance schedule accurate — update it in Garage Log.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}
