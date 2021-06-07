package com.example.groove.db.model

import com.example.groove.db.model.enums.OwnershipType
import com.example.groove.util.DateUtils.now
import com.fasterxml.jackson.annotation.JsonIgnore
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "playlist_user")
data class PlaylistUser(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		val id: Long = 0,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "playlist_id", nullable = false)
		val playlist: Playlist,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "user_id", nullable = false)
		val user: User,

		@Enumerated
		val ownershipType: OwnershipType,

		@Column(name = "created_at", nullable = false)
		var createdAt: Timestamp = now()
)
