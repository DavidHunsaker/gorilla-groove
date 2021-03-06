package com.example.groove.services

import com.example.groove.db.dao.PlaylistRepository
import com.example.groove.db.dao.PlaylistTrackRepository
import com.example.groove.db.dao.PlaylistUserRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.*
import com.example.groove.db.model.enums.OwnershipType
import com.example.groove.util.DateUtils.now
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.unwrap
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class PlaylistService(
		private val playlistRepository: PlaylistRepository,
		private val trackRepository: TrackRepository,
		private val playlistUserRepository: PlaylistUserRepository,
		private val playlistTrackRepository: PlaylistTrackRepository
) {

	@Transactional(readOnly = true)
	fun getPlaylists(user: User): List<Playlist> {
		return playlistUserRepository.getPlaylistsForUser(user)
	}

	@Transactional
	fun renamePlaylist(user: User, playlistId: Long, name: String): Playlist {
		val playlist = playlistRepository.findById(playlistId).unwrap()
				?: throw IllegalArgumentException("No playlist with ID: $playlistId found")

		if (userCanEditPlaylist(user, playlist)) {
			playlist.name = name
			playlist.updatedAt = now()
		} else {
			throw IllegalArgumentException("User has insufficient privileges to view playlist with ID: $playlistId")
		}

		playlistRepository.save(playlist)

		return playlist
	}

	@Transactional
	fun createPlaylist(user: User, name: String): Playlist {
		val playlist = Playlist(name = name)
		val playlistUser = PlaylistUser(playlist = playlist, user = user, ownershipType = OwnershipType.OWNER)

		playlistRepository.save(playlist)
		playlistUserRepository.save(playlistUser)

		return playlist
	}

	@Transactional
	fun deletePlaylist(user: User, playlistId: Long) {
		logger.info("${user.email} is attempting to delete playlist $playlistId")

		val playlist = playlistRepository.findById(playlistId).unwrap()
				?: throw IllegalArgumentException("Playlist not found with ID $playlistId")
		val playlistUser = playlist.users.find { it.user.id == user.id }
				?: throw IllegalArgumentException("Playlist not found with ID $playlistId")

		if (playlistUser.ownershipType != OwnershipType.OWNER) {
			throw IllegalArgumentException("Must be a playlist's owner to delete it.")
		}

		playlist.deleted = true
		playlistRepository.save(playlist)
	}

	@Transactional(readOnly = true)
	fun getTracks(
			name: String?,
			artist: String?,
			album: String?,
			playlistId: Long,
			searchTerm: String?,
			pageable: Pageable
	): Page<PlaylistTrack> {
		val playlist = playlistRepository.findById(playlistId).unwrap()
				?: throw IllegalArgumentException("No playlist with ID: $playlistId found")
		playlistUserRepository.findByUserAndPlaylist(loadLoggedInUser(), playlist)
				?: throw IllegalArgumentException("User has insufficient privileges to view playlist with ID: $playlistId")

		return playlistTrackRepository.getTracks(name, artist, album, playlist, searchTerm, pageable)
	}

	@Transactional
	fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
		val user = loadLoggedInUser()
		val playlist = playlistRepository.findById(playlistId)
				.orElseThrow { IllegalArgumentException("Playlist ID: $playlistId not found") }

		val tracks = trackIds.map {
			trackRepository.findById(it)
					.orElseThrow { IllegalArgumentException("Track ID: $it not found") }
		}

		if (playlist == null || !userCanEditPlaylist(user, playlist, tracks)) {
			logger.warn("User of ID ${user.id} tried to add tracks to the playlist of ID ${playlist.id} but did not have the permission to do so")
			throw IllegalArgumentException("Insufficient privileges to add the selected tracks to the playlist")
		}

		tracks.forEach {
			playlistTrackRepository.save(PlaylistTrack(playlist = playlist, track = it))
		}
	}

	@Transactional
	fun deletePlaylistTracks(playlistTrackIds: List<Long>) {
		val user = loadLoggedInUser()

		val playlistTracks = playlistTrackIds.map {
			playlistTrackRepository.findById(it)
					.orElseThrow { IllegalArgumentException("Track ID: $it not found") }
		}

		playlistTracks.forEach { playlistTrack ->
			if (userCanEditPlaylist(user, playlistTrack.playlist, listOf(playlistTrack.track))) {
				playlistTrackRepository.delete(playlistTrack)
			} else {
				logger.warn("User of ID ${user.id} tried to remove tracks from the playlist of ID ${playlistTrack.playlist.id} " +
						"but did not have the permission to do so")
				throw IllegalArgumentException("Insufficient privileges to remove the selected tracks from the playlist")
			}
		}
	}

	private fun userCanEditPlaylist(user: User, playlist: Playlist, tracks: List<Track>? = null): Boolean {
		val playlistUser = playlistUserRepository.findByUserAndPlaylist(user, playlist)

		if (playlistUser == null || !playlistUser.ownershipType.hasWritePrivilege) {
			return false
		}

		return tracks == null || tracks.all { it.user == user }
	}

	companion object {
		val logger = LoggerFactory.getLogger(PlaylistService::class.java)!!
	}
}
