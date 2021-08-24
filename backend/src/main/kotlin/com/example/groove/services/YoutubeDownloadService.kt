package com.example.groove.services

import com.example.groove.db.model.Track
import com.example.groove.db.model.User
import com.example.groove.dto.MetadataUpdateRequestDTO
import com.example.groove.dto.YoutubeDownloadDTO
import com.example.groove.properties.FileStorageProperties
import com.example.groove.properties.YouTubeDlProperties
import com.example.groove.services.enums.MetadataOverrideType
import com.example.groove.util.*
import com.fasterxml.jackson.annotation.JsonAlias
import com.google.common.annotations.VisibleForTesting
import org.springframework.stereotype.Service
import java.io.File
import java.util.*
import kotlin.math.abs


@Service
class YoutubeDownloadService(
	private val songIngestionService: SongIngestionService,
	private val fileStorageProperties: FileStorageProperties,
	private val youTubeDlProperties: YouTubeDlProperties,
	private val imageService: ImageService,
	private val metadataRequestService: MetadataRequestService
) {

	val mapper = createMapper()

	fun downloadSong(user: User, youtubeDownloadDTO: YoutubeDownloadDTO): Track {
		val url = youtubeDownloadDTO.url

		if (url.contains("&list")) {
			// If downloading a playlist, it needs to first be parsed for video IDs and split into multiple calls to this function
			throw IllegalArgumentException("Direct playlist downloads are not allowed")
		}

		val fileKey = UUID.randomUUID().toString()

		// If the user does not provide overriding data, we can attempt to parse out the title / artist / year.
		// Youtube-dl will put the additional bits of info into the file name if we give it params to do so.
		val fileName = "$fileKey|%(title)s|%(uploader)s"

		val filePath = fileStorageProperties.tmpDir + fileName

		val pb = ProcessBuilder(
			youTubeDlProperties.youtubeDlBinaryLocation + "youtube-dl",
			url,
			"--extract-audio",
			"--audio-format",
			"vorbis",
			"-o",
			"$filePath.ogg",
			"--no-cache-dir", // Ran into issues with YT giving us 403s unless we cleared cache often, so just ignore cache
			"--write-thumbnail" // this started to plop things out as .webp randomly one day
		)
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
		pb.redirectError(ProcessBuilder.Redirect.INHERIT)
		val p = pb.start()
		p.waitFor()

		// We prepended a unique prefix onto the file so that we could find it, since the full filename is not known
		// given that youtube-dl dynamically generates it with metadata from the video.
		val newSong = File(fileStorageProperties.tmpDir!!).listFiles()!!.find { file ->
			file.name.startsWith(fileKey) && file.name.endsWith(".ogg")
		}
		if (newSong == null || !newSong.exists()) {
			throw YouTubeDownloadException("Failed to download song from URL: $url")
		}

		val videoArt = chooseAlbumArt(youtubeDownloadDTO.artUrl, newSong.absolutePath) ?: run {
			logger.error("Failed to download album art for song at URL: $url!")
			null
		}

		val (videoTitle, uploaderName) = newSong.nameWithoutExtension.parseDownloadedTitle()

		// If the video title already contains a dash, it's likely the artist
		val titleToParse = if (videoTitle.containsAny(dashCharacters)) {
			videoTitle
		} else {
			// Otherwise, the artist probably isn't in the title. The next best guess is the uploader
			"$uploaderName - $videoTitle"
		}

		// The ingestion service will attempt to parse the title and artist from "titleToParse" if it isn't contained within the youtubeDownloadDTO
		val track = songIngestionService.convertAndSaveTrackForUser(youtubeDownloadDTO, user, newSong, videoArt, titleToParse)

		newSong.delete()

		// But now that the video has been parsed and its data parsed, try to find the song on spotify too.
		// That will give us the true best data possible, and we don't have to overwrite any user-provided data
		val spotifyMetadataRequest = MetadataUpdateRequestDTO(
			trackIds = listOf(track.id),
			changeAlbum = MetadataOverrideType.IF_EMPTY,
			changeAlbumArt = MetadataOverrideType.ALWAYS, // We should always get art from youtube, so gotta always replace
			changeReleaseYear = MetadataOverrideType.IF_EMPTY,
			changeTrackNumber = MetadataOverrideType.IF_EMPTY
		)
		val (successTracks, _) = metadataRequestService.requestTrackMetadata(spotifyMetadataRequest, user)

		return successTracks.firstOrNull() ?: track
	}

	// These are bits of pointless noise in some of the channels I use this on.
	// I'd like to make this be something users can customize on the fly to suit their channels.
	// I'd also like to make this slightly less naive with like, detecting either ( or [ probably with a regex. But I'll deal with that later probably maybe (probably not)
	fun stripYouTubeSpecificTerms(inputStr: String): String {
		val thingsToStrip = setOf(
			"(lyrics)",
			"(lyric video)",
			"[Monstercat Release]",
			"[Monstercat Official Music Video]",
			"[Monstercat Lyric Video]",
			"(Official Video)",
			"(Official Music Video)",
			"(Official Lyric Video)",
			"[Official Lyric Video]",
			"(Official Audio)",
			"(Original Mix)",
			"(Original Music)",
			"[Copyright Free Electronic]",
		)

		var finalTitle = inputStr
		thingsToStrip.forEach { thingToStrip ->
			finalTitle = finalTitle.replace(thingToStrip, "", ignoreCase = true)
		}

		return finalTitle.trim()
	}

	private fun String.parseDownloadedTitle(): Pair<String, String> {
		// I use a pipe for a delimiter because it seems unlikely to be used in artist names. Though honestly there's probably
		// a better character because it's a reserved character on Windows. So youtube-dl replaces it with a #
		val delimiter = if (isWindowsEnv()) "#" else "|"

		// Example, 2ace8ebf-1692-4310-91f3-147f559bf7cf|alphabet shuffle|bill wurtz
		val (title, uploader) = this.substringAfter(delimiter).split(delimiter)
		return stripYouTubeSpecificTerms(title) to uploader
	}

	// An override link can be provided for a download if we're coming from Spotify. However, if the link
	// does not exist, or fails to download, fall back to the video thumbnail so we get something
	private fun chooseAlbumArt(overrideArtLink: String?, thumbnailArtPath: String): File? {
		val videoArt = findVideoArtOnDisk(thumbnailArtPath)

		return if (overrideArtLink != null) {
			val overrideArt = imageService.downloadFromUrl(overrideArtLink)

			if (overrideArt == null) {
				if (videoArt == null) {
					logger.error("Could not grab the override album art link ${overrideArtLink}! The video thumbnail also could not be found!")
					null
				} else {
					logger.error("Could not grab the override album art link ${overrideArtLink}! Using the video thumbnail instead")
					videoArt
				}
			} else {
				videoArt?.delete()
				overrideArt
			}
		} else {
			videoArt
		}
	}

	private fun findVideoArtOnDisk(songFilePath: String): File? {
		// For some dumb reason, in December 2020 youtube-dl changed and started using the song extension
		// in the art name, which it wasn't doing for years before. So if the song has the name
		// "song.ogg" the art will have the name "song.ogg.webp". Also the art format isn't consistent here, so
		// we need to check for both webp and jpg, though it seems webp is what things are using now
		listOf("webp", "jpg").forEach { extension ->
			val newAlbumArt = File("$songFilePath.$extension")
			if (newAlbumArt.exists()) {
				return newAlbumArt
			}
		}

		return null
	}

	// target length is used to try to find videos that closely match what is found in Spotify
	fun searchYouTube(
		artist: String,
		trackName: String,
		targetLength: Int
	): List<VideoProperties> {
		val searchTerm = "$artist - $trackName"

		logger.info("Searching YouTube for the term: '$searchTerm'")
		val videosToSearch = 5
		// This combination of arguments will have Youtube-DL not actually download the videos, but instead write the
		// video details to standard out. We redirect standard out to a file to process it afterwards
		val pb = ProcessBuilder(
			youTubeDlProperties.youtubeDlBinaryLocation + "youtube-dl",
			// ¯\_(ツ)_/¯ windows + java is stupid and removes the quotes unless there's two of them I GUESS?
			if (isWindowsEnv()) "ytsearch$videosToSearch:\"\"$searchTerm\"\"" else "ytsearch$videosToSearch:\"$searchTerm\"",
			"--skip-download",
			"--dump-json",
			"--no-cache-dir"
		)

		val destination = fileStorageProperties.tmpDir + UUID.randomUUID().toString()

		val outputFile = File(destination)
		pb.redirectOutput(outputFile)
		pb.redirectError(ProcessBuilder.Redirect.INHERIT)

		logger.info(pb.command().joinToString(" "))
		val p = pb.start()
		p.waitFor()

		val fileContent = outputFile.useLines { it.toList() }

		outputFile.delete()

		return fileContent.map { videoJson ->
			val video = mapper.readValue(videoJson, VideoProperties::class.java)
			logger.info("Video found. Title: '${video.title}' Channel: '${video.channelName}'")
			video
		}.filter { videoProperties ->
			videoProperties.isValidForSong(artist, trackName, targetLength)
		}
	}

	fun getVideoPropertiesOnPlaylist(url: String): List<VideoProperties> {
		val pb = ProcessBuilder(
			youTubeDlProperties.youtubeDlBinaryLocation + "youtube-dl",
			"-i", // this ignores errors, such as a video not being found somewhere on the playlist
			"--skip-download",
			"--dump-json",
			"--no-cache-dir",
			url
		)

		val destination = fileStorageProperties.tmpDir + UUID.randomUUID().toString()

		val outputFile = File(destination)
		pb.redirectOutput(outputFile)
		pb.redirectError(ProcessBuilder.Redirect.INHERIT)

		logger.info(pb.command().joinToString(" "))
		val p = pb.start()
		p.waitFor()

		val fileContent = outputFile.useLines { it.toList() }
		outputFile.delete()

		return fileContent.map { videoJson ->
			val video = mapper.readValue(videoJson, VideoProperties::class.java)
			logger.info("Video found. Title: '${video.title}' Channel: '${video.channelName}'")
			video
		}
	}

	@Suppress("unused")
	data class VideoProperties(
		@JsonAlias("display_id")
		val id: String,

		@JsonAlias("webpage_url")
		val videoUrl: String,

		val duration: Int,
		val title: String,

		@JsonAlias("uploader")
		val channelName: String = ""
	) {
		val embedUrl: String
			get() = "https://www.youtube.com/embed/$id"
	}

	companion object {
		val logger = logger()

		// When we are checking if a YouTube video is valid for a given Spotify song, we want to make sure
		// that the song lengths more or less agree. This is the tolerance for that check.
		// e.g. Spotify says a song is 174 seconds long. We want to exclude a YT video that is 165 seconds long or 180 seconds long, or whatever. But allow a 173 second long one
		const val SONG_LENGTH_IDENTIFICATION_TOLERANCE = 4
	}
}

@VisibleForTesting
fun YoutubeDownloadService.VideoProperties.isValidForSong(artist: String, trackName: String, targetLength: Int): Boolean {
	YoutubeDownloadService.logger.info("Checking video with title: ${this.title}")

	val validLength = abs(this.duration - targetLength) < YoutubeDownloadService.SONG_LENGTH_IDENTIFICATION_TOLERANCE
	if (!validLength) {
		YoutubeDownloadService.logger.info("Video removed for bad target duration (targeted $targetLength, was ${this.duration}. Title: '${this.title}'")
		return false
	}

	val lowerTitle = this.title.toLowerCase()
	val lowerArtist = artist.toLowerCase()
	val lowerTrackName = trackName.toLowerCase()

	// Spotify can give us artists separated by commas that other places consider featured artists. So if we search
	// for what Spotify gives us blindly like "Mitis, RORY" and all the videos on YT are "Mitis - .. (feat. RORY)"
	// then we aren't going to find it. So split the artist on a comma and make sure all artists are represented
	// somewhere in the title
	val artistsFound = lowerArtist.split(",").all { individualArtist ->
		val trimmedArtist = individualArtist.trim()

		val found = lowerTitle.contains(trimmedArtist) || channelName.toLowerCase().contains(trimmedArtist)
		if (!found) {
			YoutubeDownloadService.logger.info("The artist '$trimmedArtist' was not found")
		}
		found
	}
	if (!artistsFound) {
		return false
	}

	// Now lastly we want to check that the song title is adequately represented in the video title. I ran into
	// a lot of situations where titles were slightly different so a substring match wasn't viable. So I think
	// a better approach is, like we now do with artists, check each word individually for representation, and
	// additionally get rid of words that have little value or little hope or being matched correctly
	val unimportantWords = setOf("with", "feat", "ft", "featuring")
	val titleWords = lowerTrackName
		.toLowerCase()
		.replace("(", "")
		.replace(")", "")
		.replace("[", "")
		.replace("]", "")
		.replace(".", "")
		.replace("-", "")
		.split(" ")
		.filter { it.isNotBlank() && !unimportantWords.contains(it) }

	return titleWords.all { titleWord ->
		val found = lowerTitle.contains(titleWord)
		if (!found) {
			YoutubeDownloadService.logger.info("The title word '$titleWord' was not found")
		}
		found
	}
}

class YouTubeDownloadException(error: String): RuntimeException(error)
