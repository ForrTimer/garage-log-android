package com.garagelog.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.ai.AiWorkerFactory
import com.garagelog.app.data.sync.SyncWorker
import com.garagelog.app.data.sync.SyncWorkerFactory
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.notifications.MileageReminderScheduler
import com.garagelog.app.notifications.AI_NOTIFICATION_CHANNEL_ID
import com.garagelog.app.notifications.NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GarageLogApplication : Application(), Configuration.Provider {

    lateinit var serviceLocator: ServiceLocator
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    addFactory(SyncWorkerFactory(serviceLocator.syncRepository))
                    addFactory(AiWorkerFactory(serviceLocator.aiRepository))
                },
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
        createNotificationChannel()
        applicationScope.launch {
            serviceLocator.seedIfEmpty()
            // Re-arm rather than trust the alarm survived — cheap, idempotent safety net
            // alongside BootReceiver (e.g. if the app process was killed and the alarm with it).
            val prefs = serviceLocator.notificationPrefsRepository.get()
            if (prefs.enabled) MileageReminderScheduler.reschedule(this@GarageLogApplication, prefs)
        }
        SyncWorker.schedulePeriodic(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Mileage reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminds you to log your vehicle's current mileage"
        }
        val aiChannel = NotificationChannel(
            AI_NOTIFICATION_CHANNEL_ID,
            "$ASSISTANT_NAME's answers",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when a diagnosis or reply has finished, if you've left the app"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(aiChannel)
    }
}
