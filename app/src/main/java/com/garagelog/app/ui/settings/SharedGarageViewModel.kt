package com.garagelog.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garagelog.app.data.cloud.GarageMembership
import com.garagelog.app.data.cloud.GarageSetupOutcome
import com.garagelog.app.data.cloud.GarageSetupResult
import com.garagelog.app.data.cloud.MembersPage
import com.garagelog.app.data.cloud.StandardPermissions
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.ui.locator
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The shared garage: setting one up or joining one, and (for admins) members and permissions. */
class SharedGarageViewModel(private val locator: ServiceLocator) : ViewModel() {

    val membership: StateFlow<GarageMembership> = locator.garageCloud.membership

    private val messages get() = locator.userMessages

    private val _setupRunning = MutableStateFlow(false)
    val setupRunning: StateFlow<Boolean> = _setupRunning

    private val _lastSetup = MutableStateFlow<GarageSetupResult?>(null)
    val lastSetup: StateFlow<GarageSetupResult?> = _lastSetup

    private val _pendingInvite = MutableStateFlow<GarageSetupOutcome.Invited?>(null)
    val pendingInvite: StateFlow<GarageSetupOutcome.Invited?> = _pendingInvite

    /** Signs in to Firebase if needed, then finds this account's garage or an invitation, or creates one. */
    fun setUp(activity: Activity) = viewModelScope.launch {
        if (_setupRunning.value) return@launch
        _setupRunning.value = true
        try {
            locator.authManager.signInToFirebase(activity).onFailure {
                messages.post("Couldn't sign in for the shared garage: ${it.message}")
                return@launch
            }
            val result = when (val outcome = locator.garageCloud.setUpGarage()) {
                is GarageSetupOutcome.Invited -> {
                    // Nothing joined yet: the invite dialog asks first.
                    _pendingInvite.value = outcome
                    return@launch
                }
                is GarageSetupOutcome.Ready -> outcome.result
            }
            _lastSetup.value = result
            locator.requestSync()
            messages.post(
                when {
                    !result.uploaded -> "Connected to your shared garage."
                    result.matches -> "Shared garage ready: everything uploaded."
                    else -> "Uploaded, but the cloud's counts don't match this phone's. Details in Settings."
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.post("Shared garage setup failed: ${e.message}")
        } finally {
            _setupRunning.value = false
        }
    }

    fun dismissInvite() { _pendingInvite.value = null }

    /**
     * Joins the pending invite's garage. [backupTo] = export this phone's records there first (a
     * Standard member's own vehicles are about to be removed from the phone); [clearLocal] = remove
     * them. The export finishes before anything is cleared, and a failed export stops the join.
     */
    fun acceptInvite(clearLocal: Boolean, backupTo: OutputStream? = null) = viewModelScope.launch {
        val pending = _pendingInvite.value ?: return@launch
        _setupRunning.value = true
        try {
            if (backupTo != null) {
                locator.backupManager.exportToStream(backupTo)
                messages.post("Backup exported.")
            }
            locator.garageCloud.acceptInvite(pending.invite, clearLocal)
            _pendingInvite.value = null
            locator.requestSync()
            messages.post("Joined ${pending.invite.garageName.ifBlank { "the shared garage" }}.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.post("Couldn't join: ${e.message}")
        } finally {
            _setupRunning.value = false
        }
    }

    // ---- Members screen (admins) ----

    private val _membersPage = MutableStateFlow<MembersPage?>(null)
    val membersPage: StateFlow<MembersPage?> = _membersPage

    private val _membersBusy = MutableStateFlow(false)
    val membersBusy: StateFlow<Boolean> = _membersBusy

    fun loadMembers() = membersAction(null) { }

    fun inviteMember(email: String, role: String, vehicleIds: List<String>) =
        membersAction("Invite saved. They join the next time they sign in to the shared garage.") {
            locator.garageCloud.invite(email, role, vehicleIds)
        }

    fun withdrawInvite(email: String) = membersAction("Invite withdrawn.") { locator.garageCloud.withdrawInvite(email) }

    fun updateMember(uid: String, role: String, vehicleIds: List<String>) =
        membersAction("Saved. Their phone picks it up on its next sync.") { locator.garageCloud.updateMember(uid, role, vehicleIds) }

    fun removeMember(uid: String) = membersAction("Removed from the garage.") { locator.garageCloud.removeMember(uid) }

    fun saveStandardPermissions(permissions: StandardPermissions) =
        membersAction(null) { locator.garageCloud.savePermissions(permissions) }

    /** Runs one admin action, then re-reads the page so it shows what the garage now holds. */
    private fun membersAction(done: String?, action: suspend () -> Unit) = viewModelScope.launch {
        _membersBusy.value = true
        try {
            action()
            _membersPage.value = locator.garageCloud.loadMembersPage()
            done?.let { messages.post(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.post("Couldn't update the garage: ${e.message}")
        } finally {
            _membersBusy.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SharedGarageViewModel(locator()) } }
    }
}
