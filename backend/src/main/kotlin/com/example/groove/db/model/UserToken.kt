package com.example.groove.db.model

import com.example.groove.util.DateUtils.now
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "user_token")
data class UserToken(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		val id: Long = 0,

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "user_id")
		val user: User,

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "device_id")
		var device: Device?, // Should be non-null and be a VAL when we are done migrating users to the device-on-token system

		@Column
		val token: String = "",

		@Column(name = "created_at")
		val createdAt: Timestamp = now()
)
