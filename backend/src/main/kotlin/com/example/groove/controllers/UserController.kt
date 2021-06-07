package com.example.groove.controllers

import com.example.groove.db.model.User
import com.example.groove.db.model.UserPermission
import com.example.groove.services.SyncableEntityService
import com.example.groove.services.UserService
import com.example.groove.util.loadLoggedInUser

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import javax.validation.Valid
import javax.validation.constraints.Email
import javax.validation.constraints.Size

@RestController
@RequestMapping("api/user")
class UserController(
	private val userService: UserService
) {

	// NOTE: This is available anonymously, as users now create their own accounts (with a valid invite link)
	@PostMapping("/public")
	fun createUser(@Valid @RequestBody userCreateDTO: UserCreateDTO): ResponseEntity<String> {
		val user = User(0, userCreateDTO.username, userCreateDTO.email, userCreateDTO.password)
		userService.saveUser(user, userCreateDTO.inviteLinkIdentifier)

		return ResponseEntity
			.ok()
			.build()
	}

	@GetMapping
	fun getUsers(
		@RequestParam(value = "showAll") showAll: Boolean?
	): List<UserResponseDTO> {
		return userService.getUsers(showAll ?: false).map {
			UserResponseDTO(it.id, it.name, it.email, it.lastLogin)
		}
	}

	// Only currently used by the Android 2.0 migration. 1.x saved a token but did not save the user ID, and we need that in 2.0
	@GetMapping("/self")
	fun whoAmI(): SyncableEntityService.SyncableUser {
		val loggedInUser = loadLoggedInUser()
		return SyncableEntityService.SyncableUser(
			id = loggedInUser.id,
			name = loggedInUser.name,
			lastLogin = loggedInUser.lastLogin,
			createdAt = loggedInUser.createdAt,
			updatedAt = loggedInUser.updatedAt,
			deleted = loggedInUser.deleted
		)
	}

	@GetMapping("/permissions")
	fun getOwnPermissions(): List<UserPermission> {
		return userService.getUserPermissions(loadLoggedInUser())
	}

	data class UserCreateDTO(
		@field:Size(max = 32, message = "Username can be no longer than 32 characters")
		val username: String,

		@field:Email
		val email: String,

		@field:Size(min = 9, max = 255, message = "Password must be between 9 and 255 characters long")
		val password: String,

		val inviteLinkIdentifier: String
	)

	data class UserResponseDTO(
		val id: Long,
		val username: String,
		val email: String,
		val lastLogin: Timestamp
	)
}
