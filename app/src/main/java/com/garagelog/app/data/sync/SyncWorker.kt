package com.garagelog.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Just calls SyncRepository.sync() — all the actual logic lives there so it stays testable outside WorkManager. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        syncRepository.sync()
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "garage-log-periodic-sync"
        private const val ONE_OFF_WORK_NAME = "garage-log-one-off-sync"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Registered once at app startup — WorkManager's practical minimum interval is 15 minutes. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Debounced: a mutation that fires before the previous one-off started just replaces it. */
        fun enqueueOneOff(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_OFF_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** WorkManager builds workers by reflection by default, with no way to hand them our ServiceLocator — this bypasses that. */
class SyncWorkerFactory(private val syncRepository: SyncRepository) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncRepository)
            else -> null
        }
}
