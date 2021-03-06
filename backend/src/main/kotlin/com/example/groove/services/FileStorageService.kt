package com.example.groove.services

import com.example.groove.db.dao.TrackLinkRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.Track
import com.example.groove.db.model.TrackLink
import com.example.groove.exception.ResourceNotFoundException
import com.example.groove.properties.FileStorageProperties
import com.example.groove.services.enums.AudioFormat
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.unwrap
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Timestamp
import java.util.*

abstract class FileStorageService(
		private val trackRepository: TrackRepository,
		private val trackLinkRepository: TrackLinkRepository,
		private val fileStorageProperties: FileStorageProperties
) {
	abstract fun storeSong(song: File, trackId: Long, audioFormat: AudioFormat)
	abstract fun loadSong(track: Track, audioFormat: AudioFormat): File
	abstract fun storeAlbumArt(albumArt: File, trackId: Long)
	abstract fun loadAlbumArt(trackId: Long): File?
	abstract fun copyAlbumArt(trackSourceId: Long, trackDestinationId: Long)

	abstract fun getSongLink(trackId: Long, anonymousAccess: Boolean, audioFormat: AudioFormat): String
	abstract fun getAlbumArtLink(trackId: Long, anonymousAccess: Boolean): String?
	abstract fun deleteSong(fileName: String)
	abstract fun copySong(sourceFileName: String, destinationFileName: String, audioFormat: AudioFormat)

	// Do all of this in a synchronized, new transaction to prevent race conditions with link creation / searching
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected fun getCachedTrackLink(
			trackId: Long,
			anonymousAccess: Boolean,
			audioFormat: AudioFormat = AudioFormat.MP3,
			isArtLink: Boolean,
			newLinkFun: (track: Track) -> String
	): String {
		val track = loadAuthenticatedTrack(trackId, anonymousAccess)

		synchronized(this) {
			val trackLink = if (isArtLink) {
				trackLinkRepository.findUnexpiredArtByTrackId(track.id)
			} else {
				trackLinkRepository.findUnexpiredSongByTrackIdAndAudioFormat(track.id, audioFormat)
			}

			return when {
				trackLink != null -> trackLink.link
				!anonymousAccess -> cacheLink(
						track = track,
						audioFormat = audioFormat,
						link = newLinkFun(track),
						isArt = isArtLink
				)
				else -> throw ResourceNotFoundException("No valid link found")
			}
		}
	}

	private fun cacheLink(track: Track, audioFormat: AudioFormat, link: String, isArt: Boolean): String {
		// Expire the link 1 minute earlier than 4 hours, so someone can't request the link and then
		// have Amazon revoke it right before they get a chance to stream the data
		val expirationMillis = expireHoursOut(4).time - 60_000

		val expiration = Timestamp(expirationMillis)
		val trackLink = TrackLink(
				track = track,
				link = link,
				audioFormat = audioFormat,
				expiresAt = expiration,
				isArt = isArt
		)

		trackLinkRepository.save(trackLink)

		return link
	}

	protected fun getTrackForAlbumArt(trackId: Long, anonymousAccess: Boolean): Track {
		val track = loadAuthenticatedTrack(trackId, anonymousAccess)

		// If we are doing anonymous access, also make sure that the track is within its temporary access time
		if (anonymousAccess) {
			trackLinkRepository.findUnexpiredSongByTrackIdAndAudioFormat(track.id, null)
					?: throw IllegalArgumentException("Album art for track ID: $trackId not available to anonymous users!")
		}

		return track
	}

	private fun loadAuthenticatedTrack(trackId: Long, anonymousAccess: Boolean): Track {
		val track = trackRepository.findById(trackId).unwrap() ?: throw IllegalArgumentException("No track with ID $trackId found")

		if (anonymousAccess) {
			if (track.private) {
				throw IllegalArgumentException("Insufficient privileges to access trackId $trackId")
			}
		} else {
			val user = loadLoggedInUser()
			if (track.private && user.id != track.user.id) {
				throw IllegalArgumentException("Insufficient privileges to access trackId $trackId")
			}
		}

		return track
	}

	protected fun expireHoursOut(hours: Int): Date {
		val calendar = Calendar.getInstance()
		calendar.add(Calendar.HOUR, hours)

		return calendar.time
	}

	protected fun generateTmpFilePath(): Path {
		val tmpFileName = UUID.randomUUID().toString() + ".ogg"
		return Paths.get(fileStorageProperties.tmpDir + tmpFileName)
	}
}
