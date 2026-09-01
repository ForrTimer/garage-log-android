package com.garagelog.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.garagelog.app.GarageLogApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms don't survive a reboot — re-arm the mileage reminder (if enabled) once the device is back up. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
}
