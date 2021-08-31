package com.gorilla.gorillagroove.service.sync

import com.gorilla.gorillagroove.database.dao.BaseRoomDao
import com.gorilla.gorillagroove.database.entity.DbEntity
import com.gorilla.gorillagroove.database.entity.DbSyncStatus
import com.gorilla.gorillagroove.service.GGLog.logDebug
import com.gorilla.gorillagroove.service.GGLog.logError
import java.time.Instant

abstract class StandardSynchronizer<DbType : DbEntity, ResponseType: EntityResponse>(
    // We don't want this to hold a reference to the DAO, because otherwise this can break when logging out / back in as it holds a stale ref.
    // So that is why this takes a function that returns a DAO instead, so it always get a good reference.
    private val getDaoFunc: () -> BaseRoomDao<DbType>,
) {

    suspend fun sync(
        syncStatus: DbSyncStatus,
        maximum: Instant,
        onPageSyncedHandler: suspend ((SyncType, Double) -> Unit),
    ): Boolean {
        var currentPage = 0
        var pagesToGet: Int

        do {
            val (pageToFetch, success) = savePageOfChanges(syncStatus, maximum, currentPage)
            if (!success) {
                return false
            }

            pagesToGet = pageToFetch
            currentPage++

            val percentComplete = currentPage.toDouble() / pagesToGet

            onPageSyncedHandler.invoke(syncStatus.syncType, percentComplete)
        } while (currentPage < pagesToGet)

        return true
    }

    abstract suspend fun fetchEntities(syncStatus: DbSyncStatus, maximum: Instant, page: Int): EntityChangeResponse<ResponseType>

    abstract suspend fun convertToDatabaseEntity(networkEntity: ResponseType): DbType

    protected open fun onEntityUpdate(newEntity: ResponseType) {}

    protected open fun onEntitiesModified(entities: List<DbType>) {}

    private suspend fun savePageOfChanges(syncStatus: DbSyncStatus, maximum: Instant, page: Int): Pair<Int, Boolean> {
        logDebug("Syncing $syncStatus page $page")
        val dao = getDaoFunc()

        val response = try {
            fetchEntities(syncStatus, maximum, page)
        } catch (e: Throwable) {
            logError("Could not sync page $page of ${syncStatus.syncType}!", e)
            return -1 to false
        }

        val pagesToGet = response.pageable.totalPages

        val content = response.content

        val newDbEntities = content.new.map { convertToDatabaseEntity(it) }
        val updatedDbEntities = content.modified.map {
            val entity = convertToDatabaseEntity(it)

            onEntityUpdate(it)

            entity
        }

        onEntitiesModified(updatedDbEntities)

        val entitiesToSave = newDbEntities + updatedDbEntities

        if (entitiesToSave.isNotEmpty()) {
            logDebug("Saving ${syncStatus.syncType} entities with IDs: ${entitiesToSave.map { it.id }}")

            dao.save(newDbEntities + updatedDbEntities)
        }

        val idsToDelete = content.removed
        if (idsToDelete.isNotEmpty()) {
            logDebug("Deleting ${syncStatus.syncType} entities with IDs: $idsToDelete")

            dao.delete(idsToDelete)
        }

        return pagesToGet to true
    }
}

interface EntityResponse {
    val id: Long
}
