package com.example.groove.services

import com.example.groove.controllers.MarkTrackAsListenedToDTO
import com.example.groove.db.dao.DeviceRepository
import com.example.groove.db.dao.TrackHistoryRepository
import com.example.groove.db.dao.TrackLinkRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.*
import com.example.groove.dto.UpdateTrackDTO
import com.example.groove.dto.YoutubeDownloadDTO
import com.example.groove.exception.ResourceNotFoundException
import com.example.groove.services.enums.AudioFormat
import com.example.groove.services.storage.FileStorageService
import com.example.groove.util.DateUtils.now
import com.example.groove.util.get
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.logger
import com.example.groove.util.toTimestamp

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.sql.Timestamp
import java.time.ZoneId
import java.time.ZoneOffset


@Service
class TrackService(
	private val trackRepository: TrackRepository,
	private val trackHistoryRepository: TrackHistoryRepository,
	private val deviceRepository: DeviceRepository,
	private val fileStorageService: FileStorageService,
	private val songIngestionService: SongIngestionService,
	private val youtubeDownloadService: YoutubeDownloadService,
	private val playlistService: PlaylistService,
	private val imageService: ImageService,
	private val trackLinkRepository: TrackLinkRepository
) {

	@Transactional(readOnly = true)
	fun getTracks(
		name: String?,
		artist: String?,
		album: String?,
		userId: Long?,
		searchTerm: String?,
		showHidden: Boolean,
		excludedPlaylistId: Long?,
		pageable: Pageable
	): Page<Track> {
		val loggedInId = loadLoggedInUser().id
		val idToLoad = userId ?: loggedInId
		val loadPrivate = loggedInId == idToLoad

		// The user can provide an ID of a playlist they DON'T want to see tracks from.
		// This makes it easier to add tracks to an existing playlist when you aren't sure if the tracks are already added
		val excludedTrackIds = excludedPlaylistId?.let { playlistId ->
			playlistService.getTracks(playlistId = playlistId)
				.content
				.map { it.track.id }
		}

		return trackRepository.getTracks(
			name = name,
			artist = artist,
			album = album,
			userId = idToLoad,
			loadPrivate = loadPrivate,
			loadHidden = showHidden,
			searchTerm = searchTerm,
			excludedTrackIds = excludedTrackIds,
			pageable = pageable
		)
	}

	@Transactional(readOnly = true)
	fun getAllTracksForUser(userId: Long): List<Track> {
		val loggedInId = loadLoggedInUser().id
		val loadPrivate = loggedInId == userId

		return trackRepository.getAllTracksForUser(userId = userId, loadPrivate = loadPrivate)
	}

	@Transactional(readOnly = true)
	fun getTracksByIds(ids: Set<Long>, user: User = loadLoggedInUser()): List<Track> {
		val tracks = trackRepository.findAllById(ids).toList()

		// Make sure we found a track for every ID that was requested
		if (tracks.size != ids.size) {
			val foundIds = tracks.map { it.id }.toSet()
			throw ResourceNotFoundException("Could not find tracks with IDs ${ids - foundIds}!")
		}

		// As always, make sure private tracks are only accessible to the user that owns them
		val invalidTracks = tracks.filter { track ->
			track.private && track.user.id != user.id
		}
		if (invalidTracks.isNotEmpty()) {
			val invalidIds = invalidTracks.map { it.id }.toSet()
			throw ResourceNotFoundException("Could not find tracks with IDs $invalidIds!")
		}

		return tracks
	}

	@Transactional(readOnly = true)
	fun getAllTrackCountSinceTimestamp(timestamp: Timestamp): Int {
		return trackRepository.countAllTracksAddedSinceTimestamp(timestamp)
	}

	@Transactional
	fun markSongListenedTo(deviceId: String, remoteIp: String?, data: MarkTrackAsListenedToDTO) {
		val track = trackRepository.get(data.trackId)
		val user = loadLoggedInUser()

		if (track == null || track.user.id != user.id) {
			throw IllegalArgumentException("No track found by ID ${data.trackId}!")
		}

		// May want to do some sanity checks / server side validation here to prevent this incrementing too often.
		// We know the last played date of a track and can see if it's even possible to have listened to this song
		track.playCount++
		track.lastPlayed = now()
		track.updatedAt = now()

		val savedDevice = deviceRepository.findByDeviceIdAndUser(deviceId, user.id)
			?: throw IllegalArgumentException("No device found with ID $deviceId for user ${user.name} when saving track history!")

		// Device we used might have been merged into another device. If it was, use the parent device
		// TODO can be removed when auth-token devices are the only devices! They are pre-merged
		val device = savedDevice.mergedDevice ?: savedDevice

		// Still have some kinks to work out
//		trackHistoryService.checkValidListeningTimestampForDevice(data.timeListenedAt.toInstant(), track, device)

		val localTimeNoTz = data.timeListenedAt
			// Put the timezone to be the one the user provided
			.withZoneSameInstant(ZoneId.of(data.ianaTimezone))
			// Now KEEP the time the same, but change it to be UTC. This is because MySQL wants to convert all our
			// dates to be UTC and it'll erase our actual time when we do so. So preempt it by pre-UTC-ifying our
			// timezone WHILE keeping the time unchanged so it isn't lost
			.withZoneSameLocal(ZoneOffset.UTC)
			.toLocalDateTime()

		val trackHistory = TrackHistory(
			track = track,
			device = device,
			originalDeviceId = device.id,
			ipAddress = remoteIp,
			listenedInReview = track.inReview,
			utcListenedAt = data.timeListenedAt.toInstant().toTimestamp(),
			localTimeListenedAt = localTimeNoTz.toString(),
			ianaTimezone = data.ianaTimezone,
			latitude = data.latitude,
			longitude = data.longitude
		)
		trackHistoryRepository.save(trackHistory)
	}

	@Transactional
	fun updateTracks(updatingUser: User, updateTrackDTO: UpdateTrackDTO, albumArt: MultipartFile?): List<Track> {
		val artFile = when {
			updateTrackDTO.albumArtUrl != null -> imageService.downloadFromUrl(updateTrackDTO.albumArtUrl)
			albumArt != null -> songIngestionService.storeMultipartFile(albumArt)
			else -> null
		}

		val updatedTracks = updateTrackDTO.trackIds.map { trackId ->
			val track = trackRepository.get(trackId)
			val user = loadLoggedInUser()

			if (track == null || track.user.id != user.id) {
				throw IllegalArgumentException("No track found by ID $trackId!")
			}

			updateTrackDTO.updateTrack(track)
			track.updatedAt = now()

			if (artFile != null) {
				songIngestionService.storeAlbumArtForTrack(artFile, track, updateTrackDTO.cropArtToSquare)
				track.artUpdatedAt = track.updatedAt
				trackLinkRepository.forceExpireLinksByTrackId(track.id)
			} else if (updateTrackDTO.cropArtToSquare) {
				logger.info("User ${user.name} is cropping existing art to a square for track $trackId")
				val art = fileStorageService.loadAlbumArt(trackId, ArtSize.LARGE)
				if (art == null) {
					logger.info("$trackId does not have album art to crop!")
				} else {
					songIngestionService.storeAlbumArtForTrack(art, track, true)
					trackLinkRepository.forceExpireLinksByTrackId(track.id)
					track.artUpdatedAt = track.updatedAt
					art.delete()
				}
			}

			trackRepository.save(track)
			track
		}

		artFile?.delete()

		return updatedTracks
	}

	@Transactional
	fun setPrivate(trackIds: List<Long>, private: Boolean) {
		val user = loadLoggedInUser()

		// The DB query is written such that it protects the tracks anyway. So this check is kind
		// of unnecessary. But it allows us to fail the request for the frontend
		trackRepository.findAllById(trackIds).forEach { track ->
			if (track.user.id != user.id) {
				throw IllegalArgumentException("No track with ID: ${track.id} found")
			}
		}
		trackRepository.setPrivateForUser(trackIds, loadLoggedInUser().id, private)
	}

	@Transactional
	fun deleteTracks(user: User, trackIds: List<Long>) {
		val tracks = trackRepository.findAllById(trackIds).toList()

		// Check all tracks for permissions FIRST. Otherwise, we might delete a track from disk,
		// then throw an exception and roll back the DB, but the disk deletion would not be undone
		tracks.forEach { track ->
			if (track.user.id != user.id) {
				throw IllegalArgumentException("Track with ID: ${track.id} not found")
			}
		}

		tracks.forEach { track ->
			track.deleted = true
			track.updatedAt = now()
			trackRepository.save(track)

			deleteFileIfUnused(track.fileName)
		}

		playlistService.deletePlaylistTracksForTracks(user, tracks)
	}

	private fun deleteFileIfUnused(fileName: String) {
		if (trackRepository.findAllByFileName(fileName).isNotEmpty()) {
			logger.info("The track $fileName was being deleted, but another user has this track. Skipping file delete")
			return
		}

		logger.info("Deleting song with name $fileName")
		fileStorageService.deleteSong(fileName)
	}

	@Transactional
	fun importTrack(trackIds: List<Long>): List<Track> {
		val user = loadLoggedInUser()

		val tracksToImport = trackIds.map {
			trackRepository.get(it)
		}
		val invalidTracks = tracksToImport.filter { track ->
			track == null || track.private || track.user.id == user.id
		}
		if (invalidTracks.isNotEmpty()) {
			throw IllegalArgumentException("Invalid track import request. Supplied IDs: $trackIds")
		}

		return tracksToImport.map { track ->
			val now = now()
			val forkedTrack = track!!.copy(
				id = 0,
				user = user,
				createdAt = now,
				updatedAt = now,
				addedToLibrary = now,
				playCount = 0,
				lastPlayed = null,
				hidden = false,
				originalTrack = track
			)

			trackRepository.save(forkedTrack)

			fileStorageService.copyAllAlbumArt(track.id, forkedTrack.id)

			forkedTrack
		}
	}

	fun importTrackForUser(user: User, trackId: Long): Track {
		val track = trackRepository.get(trackId)

		require(track != null && !track.private) {
			"Track with ID $trackId does not exist"
		}
		require(track.user.id != user.id) {
			"You cannot import your own track"
		}

		val now = now()
		val forkedTrack = track.copy(
			id = 0,
			user = user,
			createdAt = now,
			updatedAt = now,
			addedToLibrary = now,
			playCount = 0,
			lastPlayed = null,
			hidden = false,
			originalTrack = track
		)

		trackRepository.save(forkedTrack)

		fileStorageService.copyAllAlbumArt(track.id, forkedTrack.id)

		return track
	}

	fun getPublicTrackInfo(trackId: Long, anonymousAccess: Boolean, audioFormat: AudioFormat): PublicTrackInfoDTO {
		// This will throw an exception if anonymous access is not allowed for this file
		val trackLink = fileStorageService.getSongLink(trackId, anonymousAccess, audioFormat)

		val albumLink = fileStorageService.getAlbumArtLink(trackId, anonymousAccess, ArtSize.LARGE)

		val track = trackRepository.get(trackId)!!

		return PublicTrackInfoDTO(
			trackLink = trackLink,
			albumArtLink = albumLink,
			name = track.name,
			artist = track.artist,
			album = track.album,
			releaseYear = track.releaseYear,
			length = track.length
		)
	}

	fun adjustVolume(trackId: Long, volumeAdjustment: Double) {
		val track = trackRepository.get(trackId)

		if (track == null || track.user.id != loadLoggedInUser().id) {
			throw IllegalArgumentException("No track found by ID $trackId!")
		}

		songIngestionService.editVolume(track, volumeAdjustment)
	}

	fun trimTrack(trackId: Long, startTime: String?, duration: String?): Int {
		val track = trackRepository.get(trackId)

		if (track == null || track.user.id != loadLoggedInUser().id) {
			throw IllegalArgumentException("No track found by ID $trackId!")
		}

		val newLength = songIngestionService.trimSong(track, startTime, duration)

		track.length = newLength
		trackRepository.save(track)

		return newLength
	}

	// This track is being given to someone for review. Copy the track with the target user as
	// the new owner. Save it, and copy the album art
	fun saveTrackForUserReview(
		user: User,
		track: Track,
		reviewSource: ReviewSource,
		setAsCopied: Boolean = false
	): Track {
		logger.info("Making a copy of track ${track.id} for user ${user.name} to review")
		return track.copy(
			id = 0,
			user = user,
			reviewSource = reviewSource,
			lastReviewed = now(),
			inReview = true,
			private = false,
			hidden = false,
			addedToLibrary = null,
			createdAt = now(),
			updatedAt = now(),
			playCount = 0,
			lastPlayed = null,
			originalTrack = if (setAsCopied) track else null
		).also { trackCopy ->
			trackRepository.save(trackCopy)
			fileStorageService.copyAllAlbumArt(track.id, trackCopy.id)
		}
	}

	fun saveFromYoutube(downloadDTO: YoutubeDownloadDTO, user: User): Track {
		return youtubeDownloadService.downloadSong(user, downloadDTO).also { newTrack ->
			newTrack.addedToLibrary = newTrack.createdAt
			trackRepository.save(newTrack)
		}
	}

	companion object {
		val logger = logger()
	}
}

data class PublicTrackInfoDTO(
	val trackLink: String,
	val albumArtLink: String?,
	val name: String,
	val artist: String,
	val album: String,
	val releaseYear: Int?,
	val length: Int
)
