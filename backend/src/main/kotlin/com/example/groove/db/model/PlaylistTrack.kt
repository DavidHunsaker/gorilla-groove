package com.example.groove.db.model

import com.example.groove.util.DateUtils.now
import com.fasterxml.jackson.annotation.JsonIgnore
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "playlist_track")
class PlaylistTrack(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		override val id: Long = 0,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY, optional = false)
		@JoinColumn(name = "playlist_id")
		val playlist: Playlist,

		@ManyToOne(fetch = FetchType.LAZY, optional = false)
		@JoinColumn(name = "track_id")
		val track: Track,

		@Column(name = "sort_order")
		var sortOrder: Int = 0,

		@Column(name = "created_at")
		override var createdAt: Timestamp = now(),

		@Column(name = "updated_at")
		override var updatedAt: Timestamp = now(),

		@JsonIgnore
		@Column(columnDefinition = "BIT")
		override var deleted: Boolean = false
) : RemoteSyncable {
	val playlistId get() = playlist.id
}
