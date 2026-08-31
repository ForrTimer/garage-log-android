package com.garagelog.app.data.sync

import com.garagelog.app.data.auth.AuthManager
import com.garagelog.app.data.entity.PhotoEntity
import com.garagelog.app.data.photo.PhotoStore
import com.garagelog.app.data.repository.BuildPhaseRepository
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.PhotoRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.util.todayIso
import java.io.File
import kotlinx.serialization.json.Json

private const val SNAPSHOT_FILE_NAME = "garage-log-sync.json"
private const val PHOTO_KIND_KEY = "kind"
private const val PHOTO_KIND_VALUE = "photo"

class SyncRepository(
    private val authManager: AuthManager,
    private val driveApi: DriveApiClient,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val buildPhaseRepository: BuildPhaseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val photoRepository: PhotoRepository,
    private val photoStore: PhotoStore,
    private val statusHolder: SyncStatusHolder,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Pulls + merges + pushes everything in one pass. Silently no-ops if not signed in. */
    suspend fun sync() {
        val token = authManager.getValidAccessToken() ?: return
        statusHolder.onSyncStarted()
        runCatching {
            syncData(token)
            syncPhotos(token)
        }.onSuccess {
            statusHolder.onSyncSucceeded()
        }.onFailure { error ->
            statusHolder.onSyncFailed(error.message)
        }
    }

    private suspend fun syncData(token: String) {
        val existingFile = driveApi.findFileByName(token, SNAPSHOT_FILE_NAME)
        val remoteSnapshot = existingFile?.let {
            runCatching { json.decodeFromString(SyncSnapshot.serializer(), driveApi.downloadText(token, it.id)) }
                .getOrDefault(SyncSnapshot())
        } ?: SyncSnapshot()

        val localSnapshot = SyncSnapshot(
            vehicles = vehicleRepository.getAllForSync().map { it.toSync() },
            logs = logRepository.getAllForSync().map { it.toSync() },
            issues = issueRepository.getAllForSync().map { it.toSync() },
            buildPhases = buildPhaseRepository.getAllForSync().map { it.toSync() },
            schedules = scheduleRepository.getAllForSync().map { it.toSync() },
        )

        val merged = SyncSnapshot(
            vehicles = mergeById(localSnapshot.vehicles, remoteSnapshot.vehicles),
            logs = mergeById(localSnapshot.logs, remoteSnapshot.logs),
            issues = mergeById(localSnapshot.issues, remoteSnapshot.issues),
            buildPhases = mergeById(localSnapshot.buildPhases, remoteSnapshot.buildPhases),
            schedules = mergeById(localSnapshot.schedules, remoteSnapshot.schedules),
        )

        merged.vehicles.forEach { vehicleRepository.upsert(it.toEntity()) }
        merged.logs.forEach { logRepository.upsert(it.toEntity()) }
        merged.issues.forEach { issueRepository.upsert(it.toEntity()) }
        merged.buildPhases.forEach { buildPhaseRepository.upsert(it.toEntity()) }
        merged.schedules.forEach { scheduleRepository.upsert(it.toEntity()) }

        val mergedJson = json.encodeToString(SyncSnapshot.serializer(), merged)
        if (existingFile != null) {
            driveApi.updateTextFile(token, existingFile.id, mergedJson)
        } else {
            driveApi.createTextFile(token, SNAPSHOT_FILE_NAME, mergedJson)
        }
    }

    private suspend fun syncPhotos(token: String) {
        val localPhotos = photoRepository.getAllForSync()
        val localById = localPhotos.associateBy { it.id }

        // Push: local tombstones with a still-live Drive copy get deleted remotely, and any
        // never-uploaded live photo gets uploaded.
        for (photo in localPhotos) {
            if (photo.deleted) {
                if (photo.driveFileId != null) {
                    runCatching { driveApi.deleteFile(token, photo.driveFileId) }
                    photoRepository.upsert(photo.copy(driveFileId = null))
                }
                continue
            }
            if (photo.driveFileId == null) {
                val localFile = File(photo.filePath)
                if (localFile.exists()) {
                    val newFileId = runCatching {
                        driveApi.uploadPhoto(token, localFile, photo.id, photo.ownerType, photo.ownerId)
                    }.getOrNull()
                    if (newFileId != null) photoRepository.upsert(photo.copy(driveFileId = newFileId))
                }
            }
        }

        // Pull: any Drive photo we've never seen locally at all gets downloaded.
        val remotePhotoFiles = driveApi.listFilesByProperty(token, PHOTO_KIND_KEY, PHOTO_KIND_VALUE)
        for (remoteFile in remotePhotoFiles) {
            val photoId = remoteFile.appProperties["photoId"] ?: continue
            val ownerType = remoteFile.appProperties["ownerType"] ?: continue
            val ownerId = remoteFile.appProperties["ownerId"] ?: continue
            if (localById.containsKey(photoId)) continue // already known here, whether live or tombstoned

            val destFile = photoStore.fileForPhotoId(photoId)
            val downloaded = runCatching { driveApi.downloadToFile(token, remoteFile.id, destFile) }.isSuccess
            if (downloaded) {
                photoRepository.upsert(
                    PhotoEntity(
                        id = photoId,
                        ownerType = ownerType,
                        ownerId = ownerId,
                        filePath = destFile.absolutePath,
                        addedDate = todayIso(),
                        driveFileId = remoteFile.id,
                        updatedAt = System.currentTimeMillis(),
                        deleted = false,
                    ),
                )
            }
        }
    }
}
