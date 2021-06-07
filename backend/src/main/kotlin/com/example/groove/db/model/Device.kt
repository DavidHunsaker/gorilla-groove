package com.example.groove.db.model

import com.example.groove.db.model.enums.DeviceType
import com.example.groove.util.DateUtils.now
import com.fasterxml.jackson.annotation.JsonIgnore
import java.sql.Timestamp
import javax.persistence.*

@Entity
@Table(name = "device")
class Device(

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		val id: Long = 0,

		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "user_id")
		val user: User,

		// Multiple devices can be merged together to act as one device
		// Should only ever go 1 level deep as references are updated if multiple merges happen
		@JsonIgnore
		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "merged_device_id")
		var mergedDevice: Device? = null,

		// This column exists for record keeping. It's basically write-only. So no JPA mapping to a device or DB FKs.
		// When a device is merged, the "mergedDevice" column is updated to reflect the merged device so that the rest of the
		// application can be made simpler. If a device is merged again, all the devices are updated again, so "mergedDevice"
		// can stop being the same device that was originally merged. This can make data recovery and record keeping tricky,
		// so keep the original ID around. This means we can always follow a trail to find how a device got mapped to another
		// device, no matter how many merges took place to get there. We can then undo things manually if needed in the DB
		@JsonIgnore
		@Column(name = "original_merged_device_id")
		var originalMergedDeviceId: Long? = null,

		@JsonIgnore
		@OneToMany(mappedBy = "mergedDevice", fetch = FetchType.LAZY)
		val mergedDevices: List<Device> = mutableListOf(),

		@Enumerated
		val deviceType: DeviceType,

		@Column(name = "device_id")
		val deviceId: String,

		@Column(name = "device_name")
		var deviceName: String,

		@JsonIgnore
		@Column(columnDefinition = "BIT")
		var archived: Boolean = false,

		@Column(name = "application_version")
		var applicationVersion: String,

		@Column(name = "last_ip")
		var lastIp: String,

		@Column(name = "additional_data")
		var additionalData: String? = null,

		@Column(name = "party_enabled_until")
		var partyEnabledUntil: Timestamp? = null,

		@JsonIgnore
		@ManyToMany
		@JoinTable(
				name = "device_party_user",
				joinColumns = [JoinColumn(name = "device_id")],
				inverseJoinColumns = [JoinColumn(name = "user_id")])
		var partyUsers: MutableList<User> = mutableListOf(),

		@Column(name = "created_at")
		val createdAt: Timestamp = now(),

		@Column(name = "updated_at")
		var updatedAt: Timestamp = now()
)
