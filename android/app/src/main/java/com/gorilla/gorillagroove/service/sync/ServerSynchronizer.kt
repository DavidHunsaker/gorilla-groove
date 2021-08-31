package com.gorilla.gorillagroove.service.sync

import com.gorilla.gorillagroove.database.GorillaDatabase
import com.gorilla.gorillagroove.database.entity.DbSyncStatus
import com.gorilla.gorillagroove.di.Network
import com.gorilla.gorillagroove.service.GGLog.logDebug
import com.gorilla.gorillagroove.service.GGLog.logError
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.ui.OfflineModeService
import com.gorilla.gorillagroove.service.GGSettings
import com.gorilla.gorillagroove.util.toMutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

private const val MIN_TIME_BETWEEN_SYNCS_SECONDS = 600L // 10 minutes

object ServerSynchronizer {
    private var syncRunning = false
    private var lastSync = Instant.MIN

    private val syncStatusDao get() = GorillaDatabase.syncStatusDao

    suspend fun syncWithServer(
        syncTypes: Set<SyncType> = SyncType.values().toSet(),
        abortIfRecentlySynced: Boolean = false,
        onPageSyncedHandler: suspend ((SyncType, Double) -> Unit) = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        if (GGSettings.offlineModeEnabled) {
            return@withContext
        }

        logInfo("Running sync with API for the types: $syncTypes")
        if (abortIfRecentlySynced && lastSync.plusSeconds(MIN_TIME_BETWEEN_SYNCS_SECONDS).isAfter(Instant.now())) {
            logDebug("Last sync was too recent. Not syncing")
            return@withContext
        }

        synchronized(this) {
            if (syncRunning) {
                return@withContext
            }

            syncRunning = true
        }

        val syncTime = Instant.now()

        val syncStatuses = syncStatusDao.findAll().map { it.syncType to it }.toMutableMap()

        syncTypes.forEach { syncType ->
            // If this is the first time we have synced, a sync status might not yet exist
            val dbSyncStatus = syncStatuses[syncType] ?: DbSyncStatus(syncType = syncType, lastSynced = null, lastSyncAttempted = null).also {
                syncStatusDao.save(it)
                syncStatuses[syncType] = it
            }

            dbSyncStatus.lastSyncAttempted = syncTime
        }

        val lastModifiedTimestamps = try {
            Network.api.getLastModifiedTimestamps().lastModifiedTimestamps
        } catch (e: Throwable) {
            logError("Failed to get last modified timestamps!", e)
            synchronized(this) {
                syncStatusDao.save(syncStatuses.values.toList())
                syncRunning = false
                return@withContext
            }
        }

        syncTypes.forEach { syncType ->
            val syncStatus = syncStatuses.getValue(syncType)
            val lastSynced = syncStatus.lastSynced ?: Instant.MIN
            val lastModified = lastModifiedTimestamps[syncType.name]

            if (lastSynced.isAfter(lastModified)) {
                syncStatusDao.save(syncStatus)
            } else {
                logInfo("About to sync type: $syncType")

                val success = when (syncType) {
                    SyncType.TRACK -> TrackSynchronizer.sync(syncStatus, syncTime, onPageSyncedHandler)
                    SyncType.PLAYLIST_TRACK -> PlaylistTrackSynchronizer.sync(syncStatus, syncTime, onPageSyncedHandler)
                    SyncType.PLAYLIST -> PlaylistSynchronizer.sync(syncStatus, syncTime, onPageSyncedHandler)
                    SyncType.USER -> UserSynchronizer.sync(syncStatus, syncTime, onPageSyncedHandler)
                    SyncType.REVIEW_SOURCE -> ReviewSourceSynchronizer.sync(syncStatus, syncTime, onPageSyncedHandler)
                }

                if (success) {
                    syncStatus.lastSynced = syncTime
                    syncStatusDao.save(syncStatus)
                } else {
                    logError("Unable to completely sync type $syncType. Not saving sync date")
                }
            }
        }

        // If we attempted to sync everything, update our last in-memory sync time even if there were failures. We don't want to just spam, especially since it probably failed from bad internet.
        if (syncTypes.size == SyncType.values().size) {
            lastSync = syncTime
        }

        synchronized(this) { syncRunning = false }

        logInfo("Sync has finished running")

        OfflineModeService.downloadAlwaysOfflineTracks()
    }
}

enum class SyncType {
    TRACK,
    PLAYLIST_TRACK,
    PLAYLIST,
    USER,
    REVIEW_SOURCE
    ;
}

data class LastModifiedTimesResponse(
    val lastModifiedTimestamps: Map<String, Instant>
)

data class EntityChangeResponse<T>(
    val content: EntityChangeContent<T>,
    val pageable: EntityPagination,
) {
    data class EntityChangeContent<T>(
        val new: List<T>,
        val modified: List<T>,
        val removed: List<Long>,
    )
}

data class EntityPagination(
    val offset: Int,
    val pageSize: Int,
    val pageNumber: Int,
    val totalPages: Int,
    val totalElements: Int,
)
