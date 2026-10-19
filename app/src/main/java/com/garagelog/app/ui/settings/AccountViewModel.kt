package com.garagelog.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garagelog.app.data.auth.SignInStep
import com.garagelog.app.data.entity.NotificationPrefsEntity
import com.garagelog.app.data.sync.SyncStatus
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.notifications.MileageReminderScheduler
import com.garagelog.app.ui.locator
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Settings that are about this phone and account: Google/Drive sign-in, backups, reminders. */
class AccountViewModel(private val locator: ServiceLocator) : ViewModel() {

    val signedInEmail: StateFlow<String?> = locator.authManager.signedInEmail
    val syncStatus: StateFlow<SyncStatus> = locator.syncStatusHolder.status

    private val _pendingDriveConsent = MutableSharedFlow<IntentSender>()
    val pendingDriveConsent: SharedFlow<IntentSender> = _pendingDriveConsent

    private val messages get() = locator.userMessages

    fun syncNow() = viewModelScope.launch { locator.requestSync() }

    fun beginSignIn(activity: Activity) = viewModelScope.launch {
        locator.authManager.beginSignIn(activity).onSuccess { step ->
            when (step) {
                is SignInStep.Complete -> {
                    messages.post("Signed in as ${step.email}.")
                    locator.requestSync()
                }
                is SignInStep.NeedsDriveConsent -> _pendingDriveConsent.emit(step.intentSender)
            }
        }.onFailure { messages.post("Sign-in failed: ${it.message}") }
    }

    fun completeDriveConsent(data: Intent?) = viewModelScope.launch {
        locator.authManager.completeDriveConsent(data).onSuccess { step ->
            messages.post("Signed in as ${step.email}.")
            locator.requestSync()
        }.onFailure { messages.post("Couldn't finish connecting Drive: ${it.message}") }
    }

    fun signOut() = viewModelScope.launch {
        locator.authManager.signOut()
        messages.post("Signed out.")
    }

    fun exportBackup(output: OutputStream) = viewModelScope.launch {
        runCatching { locator.backupManager.exportToStream(output) }
            .onSuccess { messages.post("Backup exported.") }
            .onFailure { messages.post("Export failed: ${it.message}") }
    }

    fun importBackup(input: InputStream) = viewModelScope.launch {
        val result = locator.backupManager.importFromStream(input)
        messages.post(if (result.isSuccess) "Backup imported." else "Could not read that file: ${result.exceptionOrNull()?.message}")
        if (result.isSuccess) locator.requestSync()
    }

    fun resetToSample() = viewModelScope.launch {
        locator.backupManager.resetToSample()
        locator.aiRepository.deleteAll()
        messages.post("Reset to sample data.")
        locator.requestSync()
    }

    fun saveNotificationPrefs(context: Context, prefs: NotificationPrefsEntity) = viewModelScope.launch {
        val stamped = prefs.copy(updatedAt = System.currentTimeMillis())
        locator.notificationPrefsRepository.upsert(stamped)
        MileageReminderScheduler.reschedule(context.applicationContext, stamped)
        locator.requestSync()
    }

    companion object {
        val Factory = viewModelFactory { initializer { AccountViewModel(locator()) } }
    }
}
