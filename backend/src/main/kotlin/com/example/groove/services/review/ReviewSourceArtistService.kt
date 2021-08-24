package com.example.groove.services.review

import com.example.groove.db.dao.ReviewSourceArtistDownloadRepository
import com.example.groove.db.dao.ReviewSourceArtistRepository
import com.example.groove.db.dao.ReviewSourceUserRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.ReviewSourceArtist
import com.example.groove.db.model.ReviewSourceArtistDownload
import com.example.groove.db.model.ReviewSourceUser
import com.example.groove.db.model.User
import com.example.groove.dto.MetadataResponseDTO
import com.example.groove.dto.YoutubeDownloadDTO
import com.example.groove.properties.S3Properties
import com.example.groove.services.*
import com.example.groove.services.socket.ReviewQueueSocketHandler
import com.example.groove.util.DateUtils.now
import com.example.groove.util.firstAndRest
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.logger
import com.example.groove.util.minusWeeks
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewSourceArtistService(
	private val spotifyApiClient: SpotifyApiClient,
	private val reviewSourceArtistRepository: ReviewSourceArtistRepository,
	private val reviewSourceArtistDownloadRepository: ReviewSourceArtistDownloadRepository,
	private val reviewSourceUserRepository: ReviewSourceUserRepository,
	private val youtubeDownloadService: YoutubeDownloadService,
	private val trackService: TrackService,
	private val trackRepository: TrackRepository,
	private val reviewQueueSocketHandler: ReviewQueueSocketHandler,
	private val environment: Environment,
	private val s3Properties: S3Properties
) {
	@Scheduled(cron = "0 0 9 * * *") // 9 AM every day (UTC)
	@Transactional
	fun downloadNewSongs() {
		// Dev computers sometimes need to turn on 'awsStoreInS3' to run scripts against prod data, and if these
		// jobs run while that is active, they will overwrite data in S3. This is an added protection against that.
		if (s3Properties.awsStoreInS3 && !environment.activeProfiles.contains("prod")) {
			logger.info("S3 storage is on without the prod profile active. Not running Artist job")
			return
		}

		val allSources = reviewSourceArtistRepository.findAll()

		logger.info("Running Review Source Artist Downloader")

		allSources.forEach { source ->
			try {
				processSource(source)
			} catch (e: Throwable) {
				logger.error("Failed to process artist review source ${source.artistName} (${source.id})!", e)
			}
		}

		logger.info("Review Source Artist Downloader complete")
	}

	// This process can take a really long time, and we need a transaction to avoid errors like
	// "could not initialize proxy - no Session". However, I don't want one transaction because
	// then if we have an issue everything gets rolled back. Function is public only to allow
	// @Transactional annotation to work because it is a limitation of Spring Boot
    // FIXME doesn't actually seem to work because Spring doesn't allow self-invocation... https://stackoverflow.com/a/37217866
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun processSource(source: ReviewSourceArtist) {
		if (!source.isActive()) {
			return
		}
		logger.info("Checking for new songs for artist: ${source.artistName} ...")

		val users = source.getActiveUsers()

		// Spotify song releases have "day" granularity, so don't include a time component.
		// Also, Spotify might not always get songs on their service right when they are new, so
		// we can't really keep a rolling search. The way the API works right now we have to request
		// everything every time by the artist anyway.... so just grab everything from the artist, unless
		// the user specified only newer things than a certain date when creating the review source.
		val searchNewerThan = source.searchNewerThan?.toLocalDateTime()?.toLocalDate()

		val newSongs = spotifyApiClient.getSongsByArtist(source.artistName, source.artistId, searchNewerThan)
		logger.info("Found ${newSongs.size} initial songs for artist: ${source.artistName}")

		// We are going to be searching over the same music every night. Nothing we can do about it really.
		// Just have to play around the way the Spotify API works. So we have to keep track manually on what
		// songs we've already seen by a given artist
		val existingSongDiscoveries = reviewSourceArtistDownloadRepository.findByReviewSource(source)
		val discoveredSongNameToDownloadAttempt = existingSongDiscoveries.map {
			it.trackName.toLowerCase() to it
		}.toMap().toMutableMap()

		val oneWeekAgo = now().minusWeeks(1)
		val songsToDownload = newSongs.filter { newSong ->
			discoveredSongNameToDownloadAttempt[newSong.name.toLowerCase()]?.let { artistDownload ->
				// If the download was successful a prior time, exclude it from the songs to download
				if (artistDownload.downloadedAt != null) {
					return@filter false
				}

				// Otherwise, we have attempted this download before and it failed. We should only retry
				// once a week, as we have a limited quota and there's a good chance this was not a transient error
				return@filter artistDownload.lastDownloadAttempt?.before(oneWeekAgo) ?: true
			}

			// Otherwise, this is a song we've never seen before. Add a record of it
			val newDownload = ReviewSourceArtistDownload(
				reviewSource = source,
				trackName = newSong.name
			).also { reviewSourceArtistDownloadRepository.save(it) }

			// Sometimes there can be stupid data and we get the same thing returned to us, maybe on a different album but the same song.
			// Make sure we keep updating our map so that getting two of these in a row doesn't cause issues.
			discoveredSongNameToDownloadAttempt[newSong.name.toLowerCase()] = newDownload

			return@filter true
		}
		logger.info("Attempting download on ${songsToDownload.size} ${source.artistName} songs")

		val downloadCount = attemptDownloadFromYoutube(source, songsToDownload, users)
		if (downloadCount > 0) {
			users.forEach { user ->
				reviewQueueSocketHandler.broadcastNewReviewQueueContent(user.id, source, downloadCount)
			}
		}
	}

	// Now we need to find a match on YouTube to download...
	private fun attemptDownloadFromYoutube(source: ReviewSourceArtist, songsToDownload: List<MetadataResponseDTO>, users: List<User>): Int {
		val (firstUser, otherUsers) = users.firstAndRest()

		var downloadCount = 0

		songsToDownload.forEach { song ->
			val artistDownload = reviewSourceArtistDownloadRepository.findByReviewSourceAndTrackName(source, song.name)
				?: throw IllegalStateException("Could not locate an artist download with review source ID: ${source.id} and song name ${song.name}!")
			val video = youtubeDownloadService.searchYouTube(
				artist = song.artist,
				trackName = song.name,
				targetLength = song.length
			).firstOrNull()

			if (video == null) {
				logger.warn("Could not find a valid YouTube download for ${song.artist} - ${song.name}")

				artistDownload.lastDownloadAttempt = now()
				reviewSourceArtistDownloadRepository.save(artistDownload)

				return@forEach
			}

			logger.info("Found a valid match. YouTube video title: ${video.title}")
			val downloadDTO = YoutubeDownloadDTO(
				url = video.videoUrl,
				name = song.name,
				artist = song.artist,
				album = song.album,
				releaseYear = song.releaseYear,

				// Because we started from Spotify, we have a URL to the actual album art.
				// This is better than whatever it is we will get from the YT download, so pass it along to be used instead
				artUrl = song.albumArtLink,
				cropArtToSquare = true
			)

			// Sometimes YoutubeDL can have issues. Don't cascade fail all downloads because of it
			val track = try {
				youtubeDownloadService.downloadSong(firstUser, downloadDTO)
			} catch (e: Exception) {
				logger.error("Failed to download from YouTube for ${song.artist} - ${song.name}!", e)

				artistDownload.lastDownloadAttempt = now()
				reviewSourceArtistDownloadRepository.save(artistDownload)

				return@forEach
			}

			track.reviewSource = source
			track.inReview = true
			track.lastReviewed = now()
			trackRepository.save(track)

			artistDownload.lastDownloadAttempt = now()
			artistDownload.downloadedAt = artistDownload.lastDownloadAttempt
			reviewSourceArtistDownloadRepository.save(artistDownload)

			// The YT download service will save the Track for the user that downloads it.
			// So for every other user make a copy of that track
			otherUsers.forEach { otherUser ->
				trackService.saveTrackForUserReview(otherUser, track, source)
			}

			downloadCount++
		}

		return downloadCount
	}

	fun createOrFindArtistReviewSource(artistName: String): Pair<ReviewSourceArtist?, List<String>> {
		val existingSource = reviewSourceArtistRepository.findByArtistName(artistName)
		if (existingSource != null) {
			return existingSource to emptyList()
		}

		// Start by finding the artists Spotify gives us based off the name
		val artists = spotifyApiClient.searchArtistsByName(artistName)

		// Try to find an exact match. If we don't, return a list of artists to try to help the end user
		// fix any typos or things of that nature
		val targetName = artistName.toLowerCase()
		val foundArtist = artists.find { it.name.toLowerCase() == targetName }
			?: return null to artists.map { it.name }.take(5)

		// Cool cool cool we found an artist in spotify. Now just save it.
		// Hard-code "searchNewerThan" to now() until we are not throttled by YouTube and can search unlimited
		val source = ReviewSourceArtist(artistId = foundArtist.id, artistName = foundArtist.name, searchNewerThan = now())
		reviewSourceArtistRepository.save(source)

		return source to emptyList()
	}

	// addingAsSubscription means the user wants to be notified of new stuff.
	// This is not the case when they add a track to a review queue via a spotify artist search. Then it is temporary.
	fun addUserToSource(source: ReviewSourceArtist, user: User, addingAsSubscription: Boolean): ReviewSourceUser {
		if (addingAsSubscription && source.isUserSubscribed(user)) {
			throw IllegalArgumentException("User is already subscribed to artist ${source.artistName}!")
		}

		val association = reviewSourceUserRepository.findByUserAndSource(userId = user.id, sourceId = source.id) ?: run {
			logger.info("${user.name} was not previously subscribed to ${source.artistName}. Adding an association")
			val reviewSourceUser = ReviewSourceUser(reviewSource = source, user = user, active = addingAsSubscription)
			reviewSourceUser.active = false

			reviewSourceUser
		}

		association.deleted = false
		association.updatedAt = now()
		if (addingAsSubscription) {
			association.active = true
		}

		reviewSourceUserRepository.save(association)

		return association
	}

	fun addInactiveArtist(artistName: String, user: User): ReviewSourceUser {
		val source = createOrFindArtistReviewSource(artistName).first ?: run {
			logger.warn("User ${user.name} tried to add an inactive artist with name '$artistName' but it was not found")
			throw IllegalArgumentException("No artist with name '$artistName' found!")
		}

		return addUserToSource(source, user, addingAsSubscription = false)
	}

	// This function was written with a rather web-centric view in mind with the returning a list of possible things
	// that the artist could have been and I now hate it. I'd rather get web using the autocomplete endpoints to verify
	// that an artist exists, and then remove all this extra crap with the List<String>
	fun subscribeToArtist(artistName: String): Pair<ReviewSourceUser?, List<String>> {
		val currentUser = loadLoggedInUser()

		val (source, suggestions) = createOrFindArtistReviewSource(artistName)
		if (source == null) {
			return source to suggestions
		}

		// If this source was previously unused, re-enable it and update the time to search from to now
		if (source.getActiveUsers().isEmpty()) {
			logger.info("Source for $artistName has no subscribed users. Resetting its 'searchNewerThan'")
			source.updatedAt = now()
			source.searchNewerThan = now()

			reviewSourceArtistRepository.save(source)
		}

		val association = addUserToSource(source, currentUser, addingAsSubscription = true)

		return association to emptyList()
	}

	companion object {
		private val logger = logger()
	}
}

