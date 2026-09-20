package com.garagelog.app.data.ai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.garagelog.app.MainActivity
import com.garagelog.app.R
import com.garagelog.app.notifications.AI_NOTIFICATION_CHANNEL_ID

/**
 * Runs one assistant request as background work so it survives leaving the screen, switching apps,
 * or closing the app entirely — the answer lands in the database either way.
 *
 * That durability is the whole point: a request costs real money, and the previous
 * viewModelScope-based version could be torn down with the UI after the call had already been
 * billed but before the answer was saved.
 *
 * Deliberately never retries. [ListenableWorker.Result.retry] would re-run a request that may
 * already have been charged for, so a failure is reported to the user and left for them to
 * re-trigger by hand.
 */
class AiWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: AiRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        val targetId = inputData.getString(KEY_TARGET_ID) ?: return Result.failure()

        return try {
            when (kind) {
                KIND_DIAGNOSIS -> {
                    repository.runDiagnosis(targetId)
                    notify("Diagnosis ready", inputData.getString(KEY_LABEL).orEmpty())
                }
                KIND_CHAT -> {
                    val question = inputData.getString(KEY_QUESTION) ?: return Result.failure()
                    repository.runChat(targetId, inputData.getString(KEY_ISSUE_ID), question)
                    notify("$ASSISTANT_NAME replied", inputData.getString(KEY_LABEL).orEmpty())
                }
                else -> return Result.failure()
            }
            Result.success()
        } catch (e: Throwable) {
            // The failure is already recorded against the run key for the UI; the notification is
            // for the case where the owner has left the app and would otherwise never find out.
            notify("Couldn't finish that request", (e as? ClaudeException)?.userMessage ?: e.message.orEmpty())
            Result.failure()
        }
    }

    private fun notify(title: String, body: String) {
        val context = applicationContext
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AI_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wrench)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), notification) }
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KEY_TARGET_ID = "targetId"
        private const val KEY_ISSUE_ID = "issueId"
        private const val KEY_QUESTION = "question"
        private const val KEY_LABEL = "label"
        private const val KIND_DIAGNOSIS = "diagnosis"
        private const val KIND_CHAT = "chat"

        fun enqueueDiagnosis(context: Context, issueId: String, label: String) {
            enqueue(
                context,
                uniqueName = "ai-diagnosis-$issueId",
                data = workDataOf(
                    KEY_KIND to KIND_DIAGNOSIS,
                    KEY_TARGET_ID to issueId,
                    KEY_LABEL to label,
                ),
            )
        }

        fun enqueueChat(context: Context, vehicleId: String, issueId: String?, question: String, label: String) {
            enqueue(
                context,
                uniqueName = "ai-chat-${issueId ?: vehicleId}",
                data = workDataOf(
                    KEY_KIND to KIND_CHAT,
                    KEY_TARGET_ID to vehicleId,
                    KEY_ISSUE_ID to issueId,
                    KEY_QUESTION to question,
                    KEY_LABEL to label,
                ),
            )
        }

        private fun enqueue(context: Context, uniqueName: String, data: androidx.work.Data) {
            val request = OneTimeWorkRequestBuilder<AiWorker>()
                .setInputData(data)
                // Expedited so it starts now rather than waiting on WorkManager's own scheduling —
                // someone is looking at a spinner. Falls back to a normal job if the app is out of
                // expedited quota rather than throwing.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // KEEP, not REPLACE: if a request for this target is already in flight, a second tap
            // shouldn't cancel it and pay for a fresh one.
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        }
    }
}

class AiWorkerFactory(private val repository: AiRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        AiWorker::class.java.name -> AiWorker(appContext, workerParameters, repository)
        else -> null
    }
}
