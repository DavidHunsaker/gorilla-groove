package com.example.groove.db.model

import com.example.groove.db.model.enums.OfflineAvailabilityType
import com.example.groove.util.DateUtils.now
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "track")
data class Track(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		override val id: Long = 0,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "user_id", nullable = false)
		val user: User,

		@Column
		var name: String,

		@Column
		var artist: String = "",

		@Column
		var featuring: String = "",

		@Column
		var album: String = "",

		@Column(name = "track_number")
		var trackNumber: Int? = null,

		@Column(name = "file_name")
		var fileName: String,

		@Column
		var length: Int,

		@Column(name = "release_year")
		var releaseYear: Int? = null,

		@Column
		var genre: String = "",

		@Column(name = "play_count")
		var playCount: Int = 0,

		@Column(columnDefinition = "BIT") // MySQL lacks a Boolean type. Need to label it as a BIT column
		var private: Boolean = false,

		@Column(columnDefinition = "BIT")
		var hidden: Boolean = false,

		@Column(name = "last_played")
		var lastPlayed: Timestamp? = null,

		@JsonIgnore
		@Column(name = "created_at")
		override var createdAt: Timestamp = now(),

		@JsonIgnore
		@Column(name = "updated_at")
		override var updatedAt: Timestamp = now(),

		// We originally used the createdAt property to mean "addedToLibrary" because for years that's what it was.
		// With the introduction of the ReviewQueue stuff we now need to use a dedicated property for this.
		// However, to avoid having to update all the API's consumers, keep the JsonProperty name the same as it was
		@Column(name = "added_to_library")
		var addedToLibrary: Timestamp? = null,

		@JsonIgnore
		@Column(columnDefinition = "BIT")
		override var deleted: Boolean = false,

		@Column(name = "offline_availability")
		var offlineAvailability: OfflineAvailabilityType = OfflineAvailabilityType.NORMAL,

		@Column
		var note: String = "",

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "review_source_id")
		var reviewSource: ReviewSource? = null,

		@Column(name = "last_reviewed")
		var lastReviewed: Timestamp? = null,

		@Column(columnDefinition = "BIT")
		var inReview: Boolean = false,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "original_track_id")
		val originalTrack: Track? = null, // When this track is copied / recommended from an existing GG track

		@Column
		var songUpdatedAt: Timestamp = now(),

		@Column
		var artUpdatedAt: Timestamp = now(),

		@Column(name = "filesize_song_ogg")
		var filesizeSongOgg: Long,

		@Column(name = "filesize_song_mp3")
		var filesizeSongMp3: Long,

		@Column(name = "filesize_art_png")
		var filesizeArtPng: Long,

		@Column(name = "filesize_thumbnail_64x64_png")
		var filesizeThumbnail64x64Png: Long
) : RemoteSyncable {

	// TODO this is temporary until the clients update to use "addedToLibrary" instead!
	@Suppress("unused")
	@JsonProperty("createdAt")
	@Transient
	fun fakeCreatedAt(): Timestamp? = addedToLibrary

	// No longer including these as they are dumb given that we normalize all the song data when we ingest it.
	// Returning 0 instead in order to satisfy clients that might still be looking for this
	@Transient
	val bitRate: Long = 0

	@Transient
	val sampleRate: Long = 0

	val hasArt: Boolean get() = filesizeArtPng > 0

	// JsonIgnoring the main entity as we don't need to return all that data for every track.
	// But the ID is still handy to have so clients know which source a track is on
	@Suppress("unused")
	val reviewSourceId: Long? get() = reviewSource?.id
}
