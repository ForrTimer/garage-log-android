package com.garagelog.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.entity.PhotoOwnerType
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.util.todayIso
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Every change to the garage's records — vehicles, log entries, issues, maintenance schedules and
 * their photos. Each write stamps `updatedAt` (sync's last-write-wins clock) and asks for a sync.
 * Reading those records for display is [GarageLogViewModel]'s job; this one only writes.
 */
class RecordsViewModel(private val locator: ServiceLocator) : ViewModel() {

    private suspend fun requestSync() = locator.requestSync()

    // ---- Vehicles ----

    val vehicleCatalog get() = locator.vehicleCatalog

    fun saveVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        locator.vehicleRepository.upsert(vehicle.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteVehicle(id: String) = viewModelScope.launch {
        locator.vehicleRepository.getById(id)?.photoPath?.let { locator.photoStore.delete(it) }
        locator.photoRepository.softDeleteForVehicle(id)
        locator.logRepository.softDeleteForVehicle(id)
        locator.issueRepository.softDeleteForVehicle(id)
        locator.scheduleRepository.softDeleteForVehicle(id)
        locator.vehicleRepository.softDelete(id)
        // AI data is hard-deleted, not soft-deleted — it never syncs, so there's no peer that
        // needs to learn the row is gone.
        locator.aiRepository.deleteForVehicle(id)
        requestSync()
    }

    /** The two sample vehicles (SampleData). They never sync, so neither of these asks for one. */
    fun addSampleData() = viewModelScope.launch {
        locator.backupManager.addSamples()
        locator.userMessages.post("Sample vehicles added.")
    }

    fun removeSampleData() = viewModelScope.launch {
        locator.removeSampleData()
        locator.userMessages.post("Sample data removed.")
    }

    /**
     * Copies a freshly-picked photo into app storage right away and hands the resulting stable
     * file path back via [onComplete] — used by VehicleFormSheet so the sheet can preview/carry
     * the photo through to Save without holding onto the picker's own content:// Uri. That Uri's
     * read grant is short-lived (Android's Photo Picker doesn't intend it to be held onto), so a
     * photo held only as a Uri until Save was tapped would go unreadable within seconds.
     * Runs in viewModelScope rather than a Composable-scoped coroutine so the copy itself can't
     * be cancelled by anything happening to the sheet's composition while the picker is on top —
     * only the (harmless-if-dropped) UI callback is at risk of firing into a disposed sheet.
     */
    fun copyPhotoToAppStorage(sourceUri: Uri, onComplete: (String?) -> Unit) = viewModelScope.launch {
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, UUID.randomUUID().toString())
        onComplete(path)
    }

    /** Deletes a photo file directly — for a staged-but-not-yet-saved copy from [copyPhotoToAppStorage]. */
    fun deletePhotoFile(path: String) {
        locator.photoStore.delete(path)
    }

    fun setVehiclePhoto(vehicle: VehicleEntity, sourceUri: Uri) = viewModelScope.launch {
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, UUID.randomUUID().toString()) ?: return@launch
        vehicle.photoPath?.let { locator.photoStore.delete(it) }
        locator.vehicleRepository.upsert(vehicle.copy(photoPath = path, updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun removeVehiclePhoto(vehicle: VehicleEntity) = viewModelScope.launch {
        vehicle.photoPath?.let { locator.photoStore.delete(it) }
        locator.vehicleRepository.upsert(vehicle.copy(photoPath = null, updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    /**
     * The Dashboard's quick "Update mileage" dialog — deliberately lighter-weight than opening a
     * full Log entry sheet for something this routine, but every mileage change still needs to be
     * tracked for the Trends tab's odometer chart, so it writes a Mileage-category log entry
     * alongside the vehicle bump rather than updating the vehicle silently.
     */
    fun updateMileage(vehicle: VehicleEntity, mileage: Int) = viewModelScope.launch {
        val today = todayIso()
        locator.vehicleRepository.bumpMileageIfHigher(vehicle.id, mileage, today)
        locator.logRepository.upsert(
            LogEntryEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                date = today,
                mileage = mileage,
                category = LogCategory.Mileage.name,
                task = "Mileage update",
                cost = null,
                parts = "",
                notes = "",
                createdBy = locator.garageCloud.currentUid,
                createdByName = locator.garageCloud.currentStamp,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    /** Persists a new home-tab display order after the user drags a vehicle tile to a new spot. */
    fun reorderVehicles(orderedIds: List<String>) = viewModelScope.launch {
        val vehicles = locator.vehicleRepository.getAll()
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            val vehicle = vehicles.find { it.id == id } ?: return@forEachIndexed
            if (vehicle.sortOrder != index) locator.vehicleRepository.upsert(vehicle.copy(sortOrder = index, updatedAt = now))
        }
        requestSync()
    }

    /**
     * Builds [vehicleId]'s report PDF and hands its share link to [onReady] (the screen opens the
     * share sheet). A few hundred milliseconds of work, so no progress UI; a failure is a toast.
     */
    fun exportReport(vehicleId: String, onReady: (Uri) -> Unit) = viewModelScope.launch {
        try {
            onReady(locator.reportExporter.export(vehicleId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            locator.userMessages.post("Couldn't make the report: ${e.message}")
        }
    }

    // ---- Maintenance schedules ----

    /**
     * Copies every active maintenance schedule item from one vehicle onto another, as fresh
     * (not-yet-done) entries — skipping any task the target already tracks under the same name,
     * which is how a copied "Oil change" used to land beside the target's own.
     */
    fun copySchedulesToVehicle(sourceVehicleId: String, targetVehicleId: String) = viewModelScope.launch {
        val all = locator.scheduleRepository.getAll()
        val tracked = all.filter { it.vehicleId == targetVehicleId }.map { it.taskName.trim().lowercase() }.toSet()
        val (skipped, schedules) = all.filter { it.vehicleId == sourceVehicleId }
            .partition { it.taskName.trim().lowercase() in tracked }
        val now = System.currentTimeMillis()
        schedules.forEach { sched ->
            locator.scheduleRepository.upsert(
                sched.copy(
                    id = UUID.randomUUID().toString(),
                    vehicleId = targetVehicleId,
                    lastDoneMileage = null,
                    lastDoneDate = null,
                    updatedAt = now,
                ),
            )
        }
        if (schedules.isNotEmpty()) requestSync()
        val copied = if (schedules.size == 1) "Copied 1 maintenance item" else "Copied ${schedules.size} maintenance items"
        locator.userMessages.post(if (skipped.isEmpty()) "$copied." else "$copied; ${skipped.size} already tracked.")
    }

    /**
     * Adds a batch of new maintenance schedules — the Add vehicle checklist, or the ones kept from
     * a Bob suggestion. One transaction-free loop is fine at this size (a dozen rows, once).
     */
    fun addSchedules(schedules: List<MaintenanceScheduleEntity>, announce: Boolean = false) = viewModelScope.launch {
        if (schedules.isEmpty()) return@launch
        schedules.forEach { locator.scheduleRepository.upsert(it) }
        requestSync()
        if (announce) {
            locator.userMessages.post(if (schedules.size == 1) "Added 1 maintenance item." else "Added ${schedules.size} maintenance items.")
        }
    }

    fun saveSchedule(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        locator.scheduleRepository.upsert(schedule.copy(updatedAt = System.currentTimeMillis()))
        requestSync()
    }

    fun deleteSchedule(id: String) = viewModelScope.launch {
        locator.scheduleRepository.softDelete(id)
        requestSync()
    }

    fun markScheduleDoneToday(schedule: MaintenanceScheduleEntity) = viewModelScope.launch {
        val vehicle = locator.vehicleRepository.getById(schedule.vehicleId)?.takeUnless { it.deleted }
        locator.scheduleRepository.upsert(
            schedule.copy(
                lastDoneMileage = vehicle?.miles ?: schedule.lastDoneMileage,
                lastDoneDate = todayIso(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    // ---- Log entries and issues ----

    fun saveLog(entry: LogEntryEntity) = viewModelScope.launch {
        // The forms rebuild the entity, so who created it comes from the stored row, not the form.
        val stored = locator.logRepository.getById(entry.id)
        locator.logRepository.upsert(
            entry.copy(
                createdBy = stored?.createdBy ?: locator.garageCloud.currentUid,
                createdByName = stored?.createdByName ?: locator.garageCloud.currentStamp,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val mileage = entry.mileage
        if (mileage != null) {
            locator.vehicleRepository.bumpMileageIfHigher(entry.vehicleId, mileage, entry.date)
        }
        // A Routine entry linked to a schedule item (via the "Fulfills schedule" picker) is the
        // maintenance actually happening — use the log's own date/mileage (which may be backfilled
        // for a past service, not necessarily "today"/"current miles") rather than duplicating the
        // separate "mark done today" quick-action's today-and-current-mileage assumption.
        //
        // Only ever ADVANCE the schedule's lastDoneDate, never regress it. Without this guard,
        // retroactively linking several existing entries to the same schedule (editing/saving
        // them one at a time) let whichever one happened to be saved *last* silently overwrite a
        // more recent entry's already-correct due date — e.g. linking an April oil-change entry
        // after a September one was already linked reverted the schedule to "overdue since April"
        // even though the real last oil change was in September. ISO yyyy-MM-dd strings compare
        // correctly with plain string comparison. The one trade-off: correcting the *date itself*
        // on the entry that currently defines the schedule's due date (a typo fix, say) won't take
        // effect this way — use the "Last done" fields on the schedule directly (Maintenance →
        // edit) for that rarer case.
        //
        // An in-progress entry (parts bought, job not done) leaves the schedule alone; saving it as
        // finished, with its completion date, is what resets it.
        val scheduleId = entry.fulfillsScheduleId
        if (entry.category == LogCategory.Routine.name && scheduleId != null && !entry.inProgress) {
            locator.scheduleRepository.getById(scheduleId)?.takeUnless { it.deleted }?.let { schedule ->
                val currentLastDone = schedule.lastDoneDate
                if (currentLastDone == null || entry.date >= currentLastDone) {
                    locator.scheduleRepository.upsert(
                        schedule.copy(
                            lastDoneMileage = entry.mileage ?: schedule.lastDoneMileage,
                            lastDoneDate = entry.date,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        requestSync()
    }

    fun deleteLog(id: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(PhotoOwnerType.LOG.name, id).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(PhotoOwnerType.LOG.name, id)
        locator.logRepository.softDelete(id)
        requestSync()
    }

    fun saveIssue(issue: IssueEntity) = viewModelScope.launch {
        val stored = locator.issueRepository.getById(issue.id)
        locator.issueRepository.upsert(
            issue.copy(
                createdBy = stored?.createdBy ?: locator.garageCloud.currentUid,
                createdByName = stored?.createdByName ?: locator.garageCloud.currentStamp,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    fun deleteIssue(id: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(PhotoOwnerType.ISSUE.name, id).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(PhotoOwnerType.ISSUE.name, id)
        locator.issueRepository.softDelete(id)
        locator.aiRepository.deleteDiagnosisForIssue(id)
        requestSync()
    }

    // ---- Attachment photos (logs and issues) ----

    fun observePhotosForOwner(ownerType: PhotoOwnerType, ownerId: String): Flow<List<PhotoEntity>> =
        locator.photoRepository.observeForOwner(ownerType.name, ownerId)

    fun addPhoto(ownerType: PhotoOwnerType, ownerId: String, sourceUri: Uri) = viewModelScope.launch {
        val photoId = UUID.randomUUID().toString()
        val path = locator.photoStore.copyIntoAppStorage(sourceUri, photoId)
        if (path == null) {
            locator.userMessages.post("Couldn't save that photo.")
            return@launch
        }
        locator.photoRepository.upsert(
            PhotoEntity(
                id = photoId,
                ownerType = ownerType.name,
                ownerId = ownerId,
                filePath = path,
                addedDate = todayIso(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        requestSync()
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        locator.photoStore.delete(photo.filePath)
        locator.photoRepository.softDelete(photo.id)
        requestSync()
    }

    /**
     * Cleans up photos attached to an owner that's about to be discarded without ever being
     * saved — e.g. a new log entry the user picked photos onto (photos need a real id to attach
     * to, so a not-yet-saved entry's id is provisional) and then backed out of instead of saving.
     * Same soft-delete + file-delete pattern as an existing entry's own delete path.
     */
    fun discardPhotosForOwner(ownerType: PhotoOwnerType, ownerId: String) = viewModelScope.launch {
        locator.photoRepository.getForOwner(ownerType.name, ownerId).forEach { locator.photoStore.delete(it.filePath) }
        locator.photoRepository.softDeleteForOwner(ownerType.name, ownerId)
        requestSync()
    }

    companion object {
        val Factory = viewModelFactory { initializer { RecordsViewModel(locator()) } }
    }
}
