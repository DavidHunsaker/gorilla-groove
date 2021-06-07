package com.example.groove.db.model

import com.example.groove.db.model.enums.BackgroundProcessStatus
import com.example.groove.db.model.enums.BackgroundProcessType
import com.example.groove.util.DateUtils.now
import com.fasterxml.jackson.annotation.JsonIgnore
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "background_task_item")
class BackgroundTaskItem(
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		val id: Long = 0,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "user_id")
		val user: User,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "device_id")
		val originatingDevice: Device,

		@Enumerated
		var status: BackgroundProcessStatus = BackgroundProcessStatus.PENDING,

		@Enumerated
		val type: BackgroundProcessType,

		// This is the JSON that is used to complete the request for the given task
		@Column
		val payload: String,

		@Column
		var description: String,

		@Column(name = "created_at")
		val createdAt: Timestamp = now(),

		@Column(name = "updated_at")
		val updatedAt: Timestamp = now(),
)