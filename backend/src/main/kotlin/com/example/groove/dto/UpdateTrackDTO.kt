package com.example.groove.dto

import com.example.groove.db.model.Track
import com.example.groove.db.model.enums.OfflineAvailabilityType

// There are a number of ways we pass around track information outside of the Track itself.
// These are shared in all situations
interface TrackDTO {
	val name: String?
	val artist: String?
	val album: String?
	val trackNumber: Int?
	val releaseYear: Int?
	val genre: String?

	fun updateTrack(track: Track) {
		name?.let { track.name = it.trim() }
		artist?.let { track.artist = it.trim() }
		album?.let { track.album = it.trim() }
		genre?.let { track.genre = it.trim() }

		// If the user passes in a negative number, treat it as null.
		// If we didn't do this, there would be no way to let them delete stuff as a null value in the DTO indicates that we should do nothing.
		// There are ways around this but they're ugly and I don't want to do them. This closed GitHub issue is still seeing traffic.
		// So maybe one day there will be an easy way to do this. https://github.com/FasterXML/jackson-databind/issues/578#issuecomment-58443025
		trackNumber?.let { number ->
			track.trackNumber = number.takeIf { it >= 0 }
		}
		releaseYear?.let { year ->
			track.releaseYear = year.takeIf { it >= 0 }
		}
	}
}

// The ways we ingest GG songs have additional options
interface GGImportTrackDTO : TrackDTO {
	val featuring: String?
	val note: String?
	val cropArtToSquare: Boolean
}

// Options specifically for property adjustment requests
class UpdateTrackDTO(
		val trackIds: List<Long> = emptyList(),
		override val name: String? = null,
		override val artist: String? = null,
		override val album: String? = null,
		override val trackNumber: Int? = null,
		override val releaseYear: Int? = null,
		override val genre: String? = null,
		override val featuring: String? = null,
		override val cropArtToSquare: Boolean = false,
		override val note: String? = null,
		val hidden: Boolean? = null,
		val private: Boolean? = null,
		val albumArtUrl: String? = null,
		private val offlineAvailability: OfflineAvailabilityType? = null
) : GGImportTrackDTO {
	override fun updateTrack(track: Track) {
		super.updateTrack(track)

		featuring?.let { track.featuring = it.trim() }
		note?.let { track.note = it.trim() }
		hidden?.let { track.hidden = it }
		private?.let { track.private = it }
		offlineAvailability?.let { track.offlineAvailability = it }
	}
}

// The properties specifically found when parsing metadata from a physical song file on disk
class MetadataParseDTO(
		override val name: String?,
		override val artist: String?,
		override val album: String?,
		override val trackNumber: Int?,
		override val releaseYear: Int?,
		override val genre: String?,
		val length: Int
) : TrackDTO {
	override fun updateTrack(track: Track) {
		super.updateTrack(track)

		track.length = length
	}
}

// For Youtube DL ingestion method
data class YoutubeDownloadDTO(
		val url: String,
		override val name: String? = null,
		override val artist: String? = null,
		override val album: String? = null,
		override val releaseYear: Int? = null,
		override val trackNumber: Int? = null,
		override val genre: String? = null,
		override val featuring: String? = null,
		override val cropArtToSquare: Boolean = false,
		override val note: String? = null,
		val artUrl: String? = null
) : GGImportTrackDTO
