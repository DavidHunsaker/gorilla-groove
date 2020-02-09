package com.example.groove.controllers

import com.example.groove.dto.CurrentlyListeningUsersDTO
import com.example.groove.services.CurrentlyListeningService
import com.example.groove.util.loadLoggedInUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("api/currently-listening")
class CurrentlyListeningController(
		private val currentlyListeningService: CurrentlyListeningService
) {
	@GetMapping
	fun getCurrentlyListening(
			@RequestParam("lastUpdate") lastUpdate: Int
	): ResponseEntity<CurrentlyListeningUsersDTO?> {
		val currentUser = loadLoggedInUser()

		// Long poll for updates to the currently listening
		for (i in 0..NUM_CHECKS) {
			currentlyListeningService.getListeningUsersIfNew(currentUser, lastUpdate)?.let {
				return ResponseEntity.ok(it)
			}

			Thread.sleep(CHECK_INTERVAL)
		}

		return ResponseEntity(HttpStatus.NOT_FOUND)
	}

	@PostMapping
	fun setCurrentlyListening(@RequestBody body: NewCurrentlyListening) {
		currentlyListeningService.setListeningUser(loadLoggedInUser(), body.trackId, body.song, body.deviceId)
	}

	data class NewCurrentlyListening(
			@Deprecated("Clients should start sending down the track ID")
			val song: String?,

			val trackId: Long?, // Null when we stop listening to stuff

			@Deprecated("Should be not null when clients are updated")
			val deviceId: String?
	)

	companion object {
		private const val CHECK_INTERVAL = 2000L
		private const val NUM_CHECKS = 25
	}
}
