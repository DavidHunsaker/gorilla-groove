package com.example.groove.services.review

import com.example.groove.db.dao.ReviewSourceUserRepository
import com.example.groove.db.dao.ReviewSourceYoutubeChannelRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.ReviewSourceUser
import com.example.groove.db.model.ReviewSourceYoutubeChannel
import com.example.groove.db.model.User
import com.example.groove.dto.YoutubeDownloadDTO
import com.example.groove.properties.S3Properties
import com.example.groove.services.TrackService
import com.example.groove.services.YoutubeApiClient
import com.example.groove.services.YoutubeChannelInfo
import com.example.groove.services.YoutubeDownloadService
import com.example.groove.services.socket.ReviewQueueSocketHandler
import com.example.groove.util.*
import com.example.groove.util.DateUtils.now
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewSourceYoutubeChannelService(
		private val youtubeApiClient: YoutubeApiClient,
		private val reviewSourceYoutubeChannelRepository: ReviewSourceYoutubeChannelRepository,
		private val reviewSourceUserRepository: ReviewSourceUserRepository,
		private val youtubeDownloadService: YoutubeDownloadService,
		private val trackService: TrackService,
		private val trackRepository: TrackRepository,
		private val reviewQueueSocketHandler: ReviewQueueSocketHandler,
		private val environment: Environment,
		private val s3Properties: S3Properties
) {
	@Scheduled(cron = "0 0 8 * * *") // 8 AM every day (UTC)
	@Transactional
	fun downloadNewSongs() {
		// Dev computers sometimes need to turn on 'awsStoreInS3' to run scripts against prod data, and if these
		// jobs run while that is active, they will overwrite data in S3. This is an added protection against that.
		if (s3Properties.awsStoreInS3 && !environment.activeProfiles.contains("prod")) {
			logger.info("S3 storage is on without the prod profile active. Not running YT Channel job")
			return
		}

		val allSources = reviewSourceYoutubeChannelRepository.findAll()

		logger.info("Running Review Source Youtube Channel Downloader")

		allSources.forEach { source ->
			try {
				processSource(source)
			} catch (e: Throwable) {
				logger.error("Failed to process YouTube review source ${source.channelName} (${source.id})!", e)
			}
		}

		logger.info("Review Source Youtube Channel Downloader complete")
	}

	// This process can take a really long time, and we need a transaction to avoid errors like
	// "could not initialize proxy - no Session". However, I don't want one transaction because
	// then if we have an issue everything gets rolled back. Function is public only to allow
	// @Transactional annotation to work because it is a limitation of Spring Boot
	fun processSource(source: ReviewSourceYoutubeChannel) {
		if (!source.isActive()) {
			return
		}

		logger.info("Checking for new YouTube videos for channel: ${source.channelName} ...")

		val users = source.getActiveUsers()

		// We establish our own upper limit. In the very unlikely, though possible, event that a video
		// is uploaded between us creating this timestamp, and the request getting to YouTube, we need
		// to filter out the new videos or we will save it twice (since we would search for it
		// again when we run this job the next time)
		val searchedTime = now()
		val newVideos = youtubeApiClient
				.findVideos(channelId = source.channelId)
				.videos
				.filter { it.publishedAt > source.lastSearched && it.publishedAt < searchedTime}
		logger.info("Found ${newVideos.size} new video(s) for channel: ${source.channelName}")


		val (firstUser, otherUsers) = users.firstAndRest()

		var addedVideos = 0
		newVideos.forEach saveLoop@ { video ->
			logger.info("Processing ${video.title}...")
			// Some channels will put out mixes every now and then. I don't really want to download mixes automatically as they could be huge, and don't really fit the GG spirit of adding individual songs
			if (video.duration > MAX_VIDEO_LENGTH) {
				logger.info("Video ${video.title} from ${video.channelTitle} has a duration of ${video.duration} which exceeds our max allowed duration of $MAX_VIDEO_LENGTH. It will be skipped")
				return@saveLoop
			}
			// Channels will also sometimes put out announcement videos that are just annoying and get in the way of reviewing stuff. Don't download really short stuff that's unlikely to be actual music
			if (video.duration < MIN_VIDEO_LENGTH) {
				logger.info("Video ${video.title} from ${video.channelTitle} has a duration of ${video.duration} which is less than our min allowed duration of $MIN_VIDEO_LENGTH. It will be skipped")
				return@saveLoop
			}

			val (name, artist) = splitSongNameAndArtist(video.title)
			val downloadDTO = YoutubeDownloadDTO(
					url = video.videoUrl,
					name = name,
					artist = artist,
					cropArtToSquare = true
			)
			val track = youtubeDownloadService.downloadSong(firstUser, downloadDTO)
			track.reviewSource = source
			track.inReview = true
			track.lastReviewed = now()
			trackRepository.save(track)


			// The YT download service will save the Track for the user that downloads it.
			// So for every other user make a copy of that track
			otherUsers.forEach { otherUser ->
				trackService.saveTrackForUserReview(otherUser, track, source)
			}

			addedVideos++
			logger.info("Done with ${video.title}")
		}

		source.lastSearched = searchedTime
		reviewSourceYoutubeChannelRepository.save(source)

		if (addedVideos > 0) {
			users.forEach { user ->
				reviewQueueSocketHandler.broadcastNewReviewQueueContent(user.id, source, addedVideos)
			}
		}
	}

	fun splitSongNameAndArtist(songInfo: String): Pair<String, String> {
		val titleWithoutNoise = youtubeDownloadService.stripYouTubeSpecificTerms(songInfo)

		titleWithoutNoise.findIndex { dashCharacters.contains(it.toString()) }?.let { hyphenIndex ->
			return Pair(
					titleWithoutNoise.substring(hyphenIndex + 1).trim(),
					titleWithoutNoise.substring(0, hyphenIndex).trim()
			)
		} ?: return "" to titleWithoutNoise
	}

	fun subscribeToUser(youtubeName: String): ReviewSourceUser {
		val ownUser = loadLoggedInUser()
		logger.info("Subscribing ${ownUser.name} to channel $youtubeName")

		val youtubeUserInfo = youtubeApiClient.getChannelInfoByUsername(youtubeName)
				?: throw IllegalArgumentException("Unable to find YouTube channel with name $youtubeName!")

		// Check if someone is already subscribed to this channel
		// Need to grab ID first because name is unreliable

		return saveAndSubscribeToChannel(youtubeUserInfo, ownUser)
	}

	fun subscribeToChannelId(channelId: String): ReviewSourceUser {
		val ownUser = loadLoggedInUser()
		logger.info("Subscribing ${ownUser.name} to channel $channelId")

		// Check if someone is already subscribed to this channel
		reviewSourceYoutubeChannelRepository.findByChannelId(channelId)?.let { reviewSource ->
			logger.info("$channelId (${reviewSource.channelName}) already exists")
			if (reviewSource.isUserSubscribed(ownUser)) {
				throw IllegalArgumentException("User ${ownUser.name} is already subscribed to ${reviewSource.channelName}!")
			}

			if (reviewSource.getActiveUsers().isEmpty()) {
				logger.info("$channelId (${reviewSource.channelName}) already exists but has no subscribed users. Resetting its last searched")
				reviewSource.updatedAt = now()
				reviewSource.lastSearched = now() // This review source was inactive, so reset its lastSearched as if it was created new

				reviewSourceYoutubeChannelRepository.save(reviewSource)

				// If the source already existed, then the association might as well
				reviewSourceUserRepository.findByUserAndSource(userId = ownUser.id, sourceId = reviewSource.id)?.let { sourceAssociation ->
					// Association was previously deleted. Just re-enable it and return early
					sourceAssociation.deleted = false
					sourceAssociation.updatedAt = now()
					reviewSourceUserRepository.save(sourceAssociation)

					return sourceAssociation
				}
			}

			val reviewSourceUser = ReviewSourceUser(reviewSource = reviewSource, user = ownUser)
			reviewSourceUserRepository.save(reviewSourceUser)

			return reviewSourceUser
		}

		val youtubeUserInfo = youtubeApiClient.getChannelInfoByChannelId(channelId)
				?: throw IllegalArgumentException("Unable to find YouTube channel with id $channelId!")

		return saveAndSubscribeToChannel(youtubeUserInfo, ownUser)
	}

	private fun saveAndSubscribeToChannel(channelInfo: YoutubeChannelInfo, user: User): ReviewSourceUser {
		reviewSourceYoutubeChannelRepository.findByChannelId(channelInfo.id)?.let { reviewSource ->
			logger.info("${reviewSource.channelName} (${reviewSource.channelId}) already exists")
			if (reviewSource.isUserSubscribed(user)) {
				throw IllegalArgumentException("User ${user.name} is already subscribed to ${reviewSource.channelName}! (${reviewSource.channelId})")
			}

			if (reviewSource.getActiveUsers().isEmpty()) {
				logger.info("${reviewSource.channelName} already exists but has no subscribed users. Resetting its last searched")
				reviewSource.updatedAt = now()
				reviewSource.lastSearched = now() // This review source was inactive, so reset its lastSearched as if it was created new

				reviewSourceYoutubeChannelRepository.save(reviewSource)

				// If the source already existed, then the association might as well
				reviewSourceUserRepository.findByUserAndSource(userId = user.id, sourceId = reviewSource.id)?.let { sourceAssociation ->
					// Association was previously deleted. Just re-enable it and return early
					sourceAssociation.deleted = false
					sourceAssociation.updatedAt = now()
					reviewSourceUserRepository.save(sourceAssociation)

					return sourceAssociation
				}
			}

			val reviewSourceUser = ReviewSourceUser(reviewSource = reviewSource, user = user)
			reviewSourceUserRepository.save(reviewSourceUser)

			return reviewSourceUser
		}

		logger.info("Channel ${channelInfo.title} is new. Saving a new record")
		val newSource = ReviewSourceYoutubeChannel(channelId = channelInfo.id, channelName = channelInfo.title)
		reviewSourceYoutubeChannelRepository.save(newSource)

		val reviewSourceUser = ReviewSourceUser(reviewSource = newSource, user = user)
		reviewSourceUserRepository.save(reviewSourceUser)

		return reviewSourceUser
	}

	// The channel title for a YouTube channel is NOT unique, so we find the result that YouTube thinks is the most relevant
	fun subscribeToChannelTitle(channelTitle: String): ReviewSourceUser {
		val user = loadLoggedInUser()
		logger.info("Subscribing ${user.name} to channel by title $channelTitle...")

		val channelSnippets = youtubeApiClient.findChannels(channelTitle).find {
			it.channelTitle.equals(channelTitle, ignoreCase = true)
		} ?: throw IllegalArgumentException("No channel found with title $channelTitle")

		return subscribeToChannelId(channelSnippets.channelId)
	}

	companion object {
		private val logger = logger()

		const val MAX_VIDEO_LENGTH = 15 * 60 // 15 minutes
		const val MIN_VIDEO_LENGTH = 90 // 1 minute
	}
}
