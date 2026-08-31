package com.garagelog.app

import android.app.Application
import com.garagelog.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GarageLogApplication : Application() {

    lateinit var serviceLocator: ServiceLocator
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
        applicationScope.launch { serviceLocator.seedIfEmpty() }
    }
}
