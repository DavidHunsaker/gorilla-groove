package com.example.groove.controllers

import com.example.groove.dto.MetadataResponseDTO
import com.example.groove.services.SpotifyApiClient
import com.example.groove.services.YoutubeApiClient
import com.example.groove.services.YoutubeDownloadService
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.logger

import org.springframework.web.bind.annotation.*
import kotlin.math.abs

@RestController
@RequestMapping("api/search")
class SearchController(
		private val youtubeApiClient: YoutubeApiClient,
		private val spotifyApiClient: SpotifyApiClient,
		private val youtubeDownloadService: YoutubeDownloadService
) {
	@GetMapping("/youtube/term/{term}")
	fun searchYoutubeForTerm(
			@PathVariable("term") term: String
	): YoutubeApiClient.YoutubeApiResponse {
		return youtubeApiClient.findVideos(term, limit = 6)
	}

	// This is currently used by clients to try to match a spotify song to a video on youtube
	@GetMapping("/youtube/artist/{artist}/track-name/{trackName}/length/{length}")
	fun findVideoForTermAndLength(
			@PathVariable artist: String,
			@PathVariable trackName: String,
			@PathVariable length: Int
	): YoutubeDownloadService.VideoProperties? {
		logger.info("User ${loadLoggedInUser().name} is searching for videos by artist '$artist' with name '$trackName' and length '$length'")

		val properties = youtubeDownloadService.searchYouTube(
				artist = artist,
				trackName = trackName,
				targetLength = length
		).firstOrNull()
		if (properties == null) {
			logger.info("Could not find a matching video for artist $artist, name $trackName, length $length")
		}
		// Youtube-DL is currently unreliable, so I've commented out the API client's implementation here as well
//		return youtubeApiClient.findVideos(searchTerm = term)
//				.videos
//				.firstOrNull { it.duration > length - 4 && it.duration < length + 4 }
		return properties
	}

	@GetMapping("/spotify/artist/{artist}")
	fun searchSpotifyByArtist(
			@PathVariable("artist") artist: String
	): MetadataSearchResponse {
		logger.info("User ${loadLoggedInUser().name} is searching spotify for the artist '$artist'")
		return MetadataSearchResponse(
				items = spotifyApiClient.getTracksForArtistSortedByYear(artist = artist)
		)
	}

	// Mobile clients use this to do metadata updates. The flow is a little different.
	// Web picks a song, picks the fields to update, and then kicks off a request.
	// Mobile picks a song, asks for metadata, then can choose to save the changes they got
	@GetMapping("/spotify/artist/{artist}/name/{name}/length/{length}")
	fun searchSpotifyByArtistAndName(
			@PathVariable("artist") artist: String,
			@PathVariable("name") name: String,
			@PathVariable("length") length: Int
	): MetadataSearchResponse {
		logger.info("User ${loadLoggedInUser().name} is searching spotify for the artist '$artist' and name '$name' and length '$length'")
		val metadata = spotifyApiClient.getMetadataByTrackArtistAndName(artist = artist, name = name).filter {
			abs(it.length - length) < YoutubeDownloadService.SONG_LENGTH_IDENTIFICATION_TOLERANCE
		}
		return MetadataSearchResponse(items = metadata)
	}

	// Used to help suggest items to users while they are filling in forms
	@GetMapping("/autocomplete/spotify/artist-name/{partial-name}")
	fun autocompleteSpotifyArtistName(
			@PathVariable("partial-name") partialName: String
	): AutocompleteResponse {
		logger.info("User ${loadLoggedInUser().name} is querying spotify autocomplete data for the term '$partialName'")
		return AutocompleteResponse(
				// I'm not sure if these artist names are unique or not. Seems like they probably can't be, right?
				// So put them into a Set
				suggestions = spotifyApiClient.searchArtistsByName(partialName, limit = 10).map { it.name }.toSet()
		)
	}

	@GetMapping("/autocomplete/youtube/channel-name/{partial-name}")
	fun autocompleteYoutubeChannelName(
			@PathVariable("partial-name") partialName: String
	): AutocompleteResponse {
		logger.info("User ${loadLoggedInUser().name} is querying youtube autocomplete data for the term '$partialName'")
		return AutocompleteResponse(
				// Linked so it preserves order- order is the YouTube given relevance and we don't want to discard it.
				// However, channel titles are not unique and there is no benefit to returning 7 of the same name
				suggestions = LinkedHashSet(youtubeApiClient.findChannels(partialName).map { it.channelTitle })
		)
	}

	companion object {
		private val logger = logger()
	}

	data class AutocompleteResponse(val suggestions: Set<String>)
	data class MetadataSearchResponse(val items: List<MetadataResponseDTO>)
}
