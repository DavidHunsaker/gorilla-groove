package com.example.groove

import com.example.groove.properties.SpotifyApiProperties
import com.example.groove.properties.YouTubeApiProperties
import com.example.groove.services.SpotifyApiClient
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class Woohoo {

	class TestYouTubeApiProperties: YouTubeApiProperties() {
		override val youtubeApiKey: String?
			get() = "EXAMPLE_API_KEY"
	}

	/*
	@Test
	fun hey() {
		val api = YoutubeApiClient(TestYouTubeApiProperties(), RestTemplate())
		val response = api.findVideos(searchTerm = "deadmau5")

		println(response.videos.size)
	}

	@Test
	fun listen() {
		val api = YoutubeApiClient(TestYouTubeApiProperties(), RestTemplate())
		val response = api.findVideos(channelId = "UCpDJl2EmP7Oh90Vylx0dZtA")

		println(response.videos)
	}

	@Test
	fun noThanks() {
		val api = SpotifyApiClient(RestTemplate())
//		val response = api.getMetadataByTrackArtistAndName("Porter Robinson", "Vandalism", null)
//		val response = api.getMetadataByTrackArtistAndName("Ayumi Hamasaki", "Evolution", null)
//		val response = api.getMetadataByTrackArtistAndName("EMBRZ", "Sound 4 U", null)
		val response = api.getMetadataByTrackArtistAndName("LIONE", "Leave This Place", null)

		println(response)
	}
	*/

	@Test
	fun waaWaaWeeWaa() {
		val properties = SpotifyApiProperties()
		val api = SpotifyApiClient(properties, RestTemplate())

		val response = api.getMetadataByTrackArtistAndName("Ayumi Hamasaki", "Alterna")
		println(response)

//		val response = api.getSpotifyArtistId("LIONE")
//		val response = api.getAlbumsByArtistId("4Zv449cAssgslcvInvc0wa")
//		val response = api.getTracksFromAlbumIds(setOf("62MQvUh5IaT3r0bXgGqImE","6EZ7bYEiEunzlIpcM25GuG","046B22KTV1C8Hsg5XXkxjN"))

//		val testDate = LocalDate.parse("2020-02-10")
//		val response = api.getSongsByArtist("sad alex", testDate)

//		println(response.joinToString("\n"))
	}
}
