package com.example.groove.services

import com.example.groove.db.dao.*
import com.example.groove.db.model.*
import com.example.groove.db.model.enums.DeviceType
import com.example.groove.exception.ResourceNotFoundException
import com.example.groove.util.DateUtils.now
import com.example.groove.util.get
import com.example.groove.util.loadLoggedInUser
import com.example.groove.util.logger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp


@Service
class DeviceService(
		private val deviceRepository: DeviceRepository,
		private val userRepository: UserRepository,
		private val trackHistoryRepository: TrackHistoryRepository,
		private val trackRepository: TrackRepository
) {

	@Transactional(readOnly = true)
	fun getDevices(user: User): List<Device> {
		return deviceRepository.findByUser(user)
	}

	fun getDeviceById(deviceIdentifier: String) = getDeviceByIdAndUserId(deviceIdentifier, loadLoggedInUser().id)

	@Transactional(readOnly = true)
	fun getDeviceByIdAndUserId(deviceIdentifier: String, userId: Long): Device {
		val device = deviceRepository.findByDeviceIdAndUser(deviceIdentifier, userId)
				?: throw ResourceNotFoundException("No device found with identifier $deviceIdentifier and user ID: $userId")
		return device.mergedDevice ?: device
	}

	@Transactional(readOnly = true)
	fun getDeviceById(deviceId: Long): Device {
		val device = deviceRepository.get(deviceId)
				?: throw ResourceNotFoundException("No device found with id $deviceId")

		return device.mergedDevice ?: device
	}

	private fun generateDefaultName(): String {
		val adjectivesByLetter = mapOf(
				'A' to listOf("Arrogant", "Abiding", "Able", "Abrasive", "Absorbed", "Absurd", "Admiring", "Aerodynamic", "Affectionate", "Affluent", "Afraid", "Ageless"),
				'B' to listOf("Backward", "Baggy", "Baseless", "Bashful", "Basic", "Beady", "Beautiful", "Benevolent", "Beguiling", "Bitter", "Blunt"),
				'F' to listOf("Fabulous", "Fair", "Fat", "Faulty", "Fearful", "Fearless", "Fertile", "Fearsome", "Flaky", "Floppy"),
				'G' to listOf("Gleeful", "Grumpy", "Gangly", "Garish", "Grand", "Great", "Gray", "Grave", "Glistening", "Godless", "Glutinous", "Gorgeous"),
				'L' to listOf("Lacey", "Legendary", "Laughable", "Lavish", "Lawless", "Lazy", "Leafy", "Lewd", "Lengthy", "Livid", "Logical", "Loyal"),
				'M' to listOf("Macho", "Mad", "Magical", "Majestic", "Male", "Malnourished", "Manly", "Masterful", "Meager", "Mean", "Meaty", "Meek", "Mellow", "Merciless", "Messy"),
				'P' to listOf("Paranoid", "Paranormal", "Passable", "Passionate", "Passive", "Patient", "Pathetic", "Peaceful", "Peculiar", "Pedantic", "Perky", "Perplexing", "Petite"),
				'S' to listOf("Sacred", "Sad", "Sadistic", "Salty", "Sane", "Sassy", "Satisfying", "Saucy", "Scandalous", "Skeptical", "Scholarly", "Scattered", "Secretive", "Seductive", "Sincere")
		)
		val characterNames = listOf("Gollum", "Frodo", "Samwise", "Aragorn", "Legolas", "Gandalf", "Sauron", "Arwen", "Bilbo", "Gimli", "Boromir", "Faramir", "Merry", "Pippin")

		val characterName = characterNames.random()
		val adjective = adjectivesByLetter[characterName.first().toUpperCase()]?.random() ?: ""

		return "$adjective $characterName"
	}

	@Transactional
	fun createOrUpdateDevice(
			user: User,
			deviceId: String,
			deviceType: DeviceType,
			preferredDeviceName: String? = null,
			version: String,
			ipAddress: String?,
			additionalData: String?
	): Device {
		val device = deviceRepository.findByDeviceIdAndUser(deviceId, user.id) ?: run {
			logger.info("First time seeing device $deviceId for user ${user.name}")
			Device(
					user = user,
					deviceId = deviceId,
					deviceName = preferredDeviceName ?: generateDefaultName(),
					deviceType = deviceType,
					applicationVersion = version,
					lastIp = ipAddress ?: "0.0.0.0"
			)
		}

		// If this device has been merged, we need to update the version of the parent
		val deviceToUpdate = device.mergedDevice ?: device

		migrateDeviceIfNeeded(deviceToUpdate, version)

		deviceToUpdate.applicationVersion = version
		deviceToUpdate.updatedAt = now()
		deviceToUpdate.additionalData = additionalData
		ipAddress?.let { deviceToUpdate.lastIp = ipAddress }

		deviceRepository.save(deviceToUpdate)

		return deviceToUpdate
	}

	private fun findActiveDevice(id: Long): Device {
		val device = deviceRepository.get(id)
		if (device == null || device.user.id != loadLoggedInUser().id) {
			throw ResourceNotFoundException("No device found with ID $id")
		}

		return device
	}

	private fun migrateDeviceIfNeeded(device: Device, newVersion: String) {
		val iosMigrationVersion = "2.1.0.1"

		if (device.deviceType == DeviceType.IPHONE) {
			// A smart person would check if the prior version was lower than this, and then do the migration.
			// But I am a lazy person with two iOS users other than myself. So lazy wins out today.
			if (device.applicationVersion != iosMigrationVersion && newVersion == iosMigrationVersion) {
				logger.info("User ${device.user.name} is migrating iOS version $iosMigrationVersion")
				trackRepository.setUpdatedAtForActiveTracksAndUser(device.user.id)
			}
		}
	}

	@Transactional
	fun updateDevice(id: Long, deviceName: String) {
		val device = findActiveDevice(id)

		device.deviceName = deviceName
		deviceRepository.save(device)
	}

	@Transactional
	fun archiveDevice(id: Long, archived: Boolean) {
		val device = findActiveDevice(id)

		device.archived = archived
		deviceRepository.save(device)
	}

	@Transactional
	fun deleteDevice(id: Long) {
		val device = findActiveDevice(id)

		// Database handles nulling the references to this in TrackHistory
		deviceRepository.delete(device)
	}

	@Transactional
	fun mergeDevices(id: Long, targetId: Long) {
		val user = loadLoggedInUser()

		logger.info("Merging device $id into $targetId for user ${user.name}")

		val device = findActiveDevice(id)
		val targetDevice = findActiveDevice(targetId)

		if (device.deviceType != targetDevice.deviceType) {
			throw IllegalArgumentException("Cannot merge two devices that have a different device type!")
		}

		device.mergedDevice = targetDevice
		device.originalMergedDeviceId = targetDevice.id

		deviceRepository.save(device)

		// Update all our old references so that they point to just one device- the one we merged into
		trackHistoryRepository.updateDevice(newDevice = targetDevice, oldDevice = device)

		if (device.mergedDevices.isNotEmpty()) {
			logger.info("Merging ${device.mergedDevices.size} additional device(s) into $targetId")
			device.mergedDevices.forEach {
				it.mergedDevice = targetDevice
				deviceRepository.save(it)
			}
		}
	}

	@Transactional
	fun enableParty(deviceIdentifier: String, partyUntil: Timestamp, partyUserIds: Set<Long>): Device {
		val currentDevice = getDeviceById(deviceIdentifier)

		currentDevice.partyEnabledUntil = partyUntil
		currentDevice.partyUsers.removeAll { true }
		partyUserIds.forEach { userId ->
			val user = userRepository.get(userId)
					?: throw ResourceNotFoundException("No user found with ID $userId!")

			require(currentDevice.user.id != userId) {
				"Cannot add own user as a party user!"
			}

			currentDevice.partyUsers.add(user)
		}

		deviceRepository.save(currentDevice)

		return currentDevice
	}

	@Transactional
	fun disableParty(deviceIdentifier: String): Device {
		val currentDevice = getDeviceById(deviceIdentifier)

		currentDevice.partyEnabledUntil = null
		currentDevice.partyUsers.removeAll { true }

		deviceRepository.save(currentDevice)

		return currentDevice
	}

	companion object {
		private val logger = logger()
	}
}
