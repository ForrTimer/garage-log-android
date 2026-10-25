package com.garagelog.app.ui

import androidx.compose.runtime.Composable
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.ui.issues.IssueFormSheet
import com.garagelog.app.ui.log.LogFormSheet
import com.garagelog.app.ui.schedule.BobScheduleSheet
import com.garagelog.app.ui.schedule.ScheduleFormSheet
import com.garagelog.app.ui.settings.VehicleFormSheet
import com.garagelog.app.util.todayIso

/** The add/edit sheet open over the app, if any — one at a time. */
internal sealed class Sheet {
    data object None : Sheet()
    data class VehicleForm(val vehicle: VehicleEntity?) : Sheet()
    data class LogForm(val entry: LogEntryEntity?, val prefill: LogEntryEntity? = null) : Sheet()
    data class IssueForm(val issue: IssueEntity?) : Sheet()
    data class ScheduleForm(val schedule: MaintenanceScheduleEntity?) : Sheet()
    data class BobSchedule(val vehicle: VehicleEntity) : Sheet()
}

/**
 * A new log entry with the obvious fields already filled — "Add fueling", logging a scheduled
 * service, logging the repair that resolved an issue. Nothing is saved until the sheet's Save.
 */
internal fun newLogSheet(
    vehicleId: String,
    category: LogCategory,
    task: String,
    mileage: Int?,
    fulfillsScheduleId: String? = null,
) = Sheet.LogForm(
    entry = null,
    prefill = LogEntryEntity(
        id = "",
        vehicleId = vehicleId,
        date = todayIso(),
        mileage = mileage,
        category = category.name,
        task = task,
        cost = null,
        parts = "",
        notes = "",
        fulfillsScheduleId = fulfillsScheduleId,
    ),
)

/**
 * Shows [sheet] wired to the records it edits; [onShow] switches to another sheet (or
 * [Sheet.None] to close). Locked and delete states come from [access], so a Standard member of a
 * shared garage sees read-only forms where their permissions say so.
 */
@Composable
internal fun AppSheets(
    sheet: Sheet,
    uiState: GarageLogUiState,
    access: Access,
    records: RecordsViewModel,
    onShow: (Sheet) -> Unit,
) {
    val close = { onShow(Sheet.None) }
    val defaultVehicleId = uiState.activeVehicleId ?: uiState.vehicles.firstOrNull()?.id
    when (sheet) {
        is Sheet.None -> Unit
        is Sheet.VehicleForm -> VehicleFormSheet(
            vehicle = sheet.vehicle,
            onDismiss = close,
            onSave = { v, starterSchedules ->
                records.saveVehicle(v)
                records.addSchedules(starterSchedules)
                close()
            },
            onDelete = { records.deleteVehicle(it); close() },
            lockedReason = if (sheet.vehicle != null && !access.canEditVehicles) Access.VEHICLE_LOCKED else null,
            canDelete = access.canDeleteVehicles,
        )
        is Sheet.LogForm -> LogFormSheet(
            entry = sheet.entry,
            prefill = sheet.prefill,
            vehicles = uiState.vehicles,
            schedules = uiState.schedules,
            defaultVehicleId = defaultVehicleId,
            onDismiss = close,
            onSave = { records.saveLog(it); close() },
            onDelete = { records.deleteLog(it); close() },
            lockedReason = sheet.entry?.let { access.entryLockReason(it.createdBy) },
            canDelete = sheet.entry?.let { access.canDeleteEntry(it.createdBy) } ?: true,
            stamp = sheet.entry?.createdByName?.takeIf { access.showsStamps },
        )
        is Sheet.IssueForm -> IssueFormSheet(
            issue = sheet.issue,
            vehicles = uiState.vehicles,
            defaultVehicleId = defaultVehicleId,
            onDismiss = close,
            onSave = { records.saveIssue(it); close() },
            onDelete = { records.deleteIssue(it); close() },
            lockedReason = sheet.issue?.let { access.entryLockReason(it.createdBy) },
            canDelete = sheet.issue?.let { access.canDeleteEntry(it.createdBy) } ?: true,
            stamp = sheet.issue?.createdByName?.takeIf { access.showsStamps },
            onResolvedWithLog = { resolved ->
                records.saveIssue(resolved)
                onShow(
                    newLogSheet(
                        vehicleId = resolved.vehicleId,
                        category = LogCategory.Repair,
                        task = resolved.title,
                        mileage = uiState.vehicles.find { it.id == resolved.vehicleId }?.miles,
                    ),
                )
            },
        )
        is Sheet.ScheduleForm -> ScheduleFormSheet(
            schedule = sheet.schedule,
            vehicles = uiState.vehicles,
            defaultVehicleId = defaultVehicleId,
            onDismiss = close,
            onSave = { records.saveSchedule(it); close() },
            onDelete = { records.deleteSchedule(it); close() },
            lockedReason = if (access.canEditSchedules) null else Access.SCHEDULE_LOCKED,
        )
        is Sheet.BobSchedule -> BobScheduleSheet(
            vehicle = sheet.vehicle,
            existing = uiState.schedules.filter { it.vehicleId == sheet.vehicle.id },
            onDismiss = close,
            onAdd = { records.addSchedules(it, announce = true); close() },
        )
    }
}
