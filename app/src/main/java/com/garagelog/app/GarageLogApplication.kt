package com.garagelog.app

import android.app.Application
import androidx.work.Configuration
import com.garagelog.app.data.sync.SyncWorker
import com.garagelog.app.data.sync.SyncWorkerFactory
import com.garagelog.app.di.ServiceLocator
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
            .setWorkerFactory(SyncWorkerFactory(serviceLocator.syncRepository))
            .build()

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
        applicationScope.launch { serviceLocator.seedIfEmpty() }
        SyncWorker.schedulePeriodic(this)
    }
}
