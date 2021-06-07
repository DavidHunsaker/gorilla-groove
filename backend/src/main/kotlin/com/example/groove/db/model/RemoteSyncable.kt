package com.example.groove.db.model

import com.example.groove.db.dao.GGEntity
import java.sql.Timestamp

interface RemoteSyncable : SyncDTO, GGEntity {
	override val id: Long

	val createdAt: Timestamp

	var updatedAt: Timestamp

	var deleted: Boolean

	// This is what actually gets returned to the mobile apps, with any cruft trimmed out
	fun toSyncDTO(): SyncDTO { return this }
}

interface SyncDTO {
	val id: Long
}
