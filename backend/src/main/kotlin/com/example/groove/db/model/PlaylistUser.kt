package com.example.groove.db.model

import com.example.groove.db.model.enums.OwnershipType
import com.fasterxml.jackson.annotation.JsonIgnore
import java.sql.Timestamp
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "playlist_user")
data class PlaylistUser(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		val id: Long = 0,

		@JsonIgnore
		@ManyToOne
		@JoinColumn(name = "playlist_id", nullable = false)
		val playlist: Playlist,

		@JsonIgnore
		@ManyToOne
		@JoinColumn(name = "user_id", nullable = false)
		val user: User,

		@Enumerated
		val ownershipType: OwnershipType,

		@Column(name = "created_at", nullable = false)
		var createdAt: Timestamp = Timestamp(Date().time)
)
