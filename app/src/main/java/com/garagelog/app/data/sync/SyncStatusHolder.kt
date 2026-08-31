package com.garagelog.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
)

/** Shared between SyncWorker (writer) and the Settings screen (reader) — no persistence needed, process-lifetime only. */
class SyncStatusHolder {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun onSyncStarted() {
        _status.value = _status.value.copy(isSyncing = true, lastError = null)
    }

    fun onSyncSucceeded() {
        _status.value = _status.value.copy(isSyncing = false, lastSyncedAt = System.currentTimeMillis(), lastError = null)
    }

    fun onSyncFailed(message: String?) {
        _status.value = _status.value.copy(isSyncing = false, lastError = message ?: "Sync failed")
    }
}
