package com.garagelog.app.ui

import com.garagelog.app.data.cloud.GarageMembership

/**
 * What this person may do in the app, from their shared-garage role and the garage's Standard
 * permissions. Not in a garage, or an admin = everything. The UI hides or locks what this says no
 * to; firebase/firestore.rules is what actually enforces it.
 */
data class Access(val membership: GarageMembership = GarageMembership(null, null, null)) {
    private val standard = membership.isStandard
    private val allowed = membership.permissions

    val canAddVehicles: Boolean get() = !standard
    val canDeleteVehicles: Boolean get() = !standard
    val canEditVehicles: Boolean get() = !standard || allowed.editVehicles
    val canEditSchedules: Boolean get() = !standard || allowed.editSchedules
    /** Standard members get diagnosis only: chat would run up the garage owner's Bob bill. */
    val canChatWithBob: Boolean get() = !standard
    val canManageMembers: Boolean get() = membership.isAdmin

    fun canEditEntry(createdBy: String): Boolean =
        !standard || allowed.editOthersEntries || (createdBy.isNotEmpty() && createdBy == membership.uid)

    fun canDeleteEntry(createdBy: String): Boolean = !standard || (allowed.deleteEntries && canEditEntry(createdBy))

    /** Why an entry's form is read-only, or null if it isn't. */
    fun entryLockReason(createdBy: String): String? =
        if (canEditEntry(createdBy)) null else "Someone else added this. Your garage's admins have editing other people's entries turned off."

    companion object {
        const val VEHICLE_LOCKED = "Only admins can change this vehicle's details in your shared garage."
        const val SCHEDULE_LOCKED = "Only admins can change maintenance schedules in your shared garage."
    }
}
