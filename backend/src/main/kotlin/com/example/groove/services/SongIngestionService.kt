package com.example.groove.services

import com.example.groove.db.dao.TrackLinkRepository
import com.example.groove.db.dao.TrackRepository
import com.example.groove.db.model.User
import com.example.groove.db.model.Track
import com.example.groove.dto.GGImportTrackDTO
import com.example.groove.dto.UpdateTrackDTO
import com.example.groove.exception.FileStorageException
import com.example.groove.properties.FileStorageProperties
import com.example.groove.services.enums.AudioFormat
import com.example.groove.services.storage.FileStorageService
import com.example.groove.util.*
import com.example.groove.util.DateUtils.now
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import javax.imageio.ImageIO

@Service
class SongIngestionService(
		fileStorageProperties: FileStorageProperties,
		private val ffmpegService: FFmpegService,
		private val fileMetadataService: FileMetadataService,
		private val trackRepository: TrackRepository,
		private val trackLinkRepository: TrackLinkRepository,
		private val fileStorageService: FileStorageService,
		private val imageService: ImageService
) {

	private val fileStorageLocation: Path = Paths.get(fileStorageProperties.tmpDir!!)
			.toAbsolutePath().normalize()

	init {
		try {
			Files.createDirectories(this.fileStorageLocation)
		} catch (ex: Exception) {
			throw FileStorageException("Could not create the directory where the uploaded files will be stored.", ex)
		}
	}

	fun storeMultipartFile(file: MultipartFile): File {
		// Discard the file's original name; but keep the extension
		val extension = file.originalFilename!!.split(".").last()
		val fileName = UUID.randomUUID().toString() + "." + extension

		// Copy the song to a temporary location for further processing
		val targetLocation = fileStorageLocation.resolve(fileName)
		Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)

		val newFile = File(targetLocation.toUri())

		if (!newFile.exists()) {
			throw IllegalStateException("Could not store multipart file!")
		}

		return newFile
	}

	fun storeSongForUser(songFile: MultipartFile, user: User): Track {
		logger.info("Storing song ${songFile.originalFilename} for user: ${user.name}")

		val tmpSongFile = storeMultipartFile(songFile)
		val tmpImageFile = ripAndSaveAlbumArt(tmpSongFile)

		val track = convertAndSaveTrackForUser(
				trackDTO = UpdateTrackDTO(),
				user = user,
				song = tmpSongFile,
				art = tmpImageFile,
				originalFileName = songFile.originalFilename!!
		)

		track.addedToLibrary = now()
		trackRepository.save(track)

		tmpSongFile.delete()
		tmpImageFile?.delete()

		return track
	}

	@Transactional
	fun storeAlbumArtForTrack(albumArt: File, track: Track, cropImageToSquare: Boolean) {
		ArtSize.values().forEach { artSize ->
			logger.info("Beginning album art storage for track ID: ${track.id} and art size of: $artSize")

			val convertedArt = imageService.convertToStandardArtFile(albumArt, artSize, cropImageToSquare) ?: return

			fileStorageService.storeAlbumArt(convertedArt, track.id, artSize)

			when (artSize) {
				ArtSize.LARGE -> track.filesizeArtPng = convertedArt.length()
				ArtSize.SMALL -> track.filesizeThumbnail64x64Png = convertedArt.length()
			}

			convertedArt.delete()
			logger.info("Finished album art storage for track ID: ${track.id} and art size of: $artSize")
		}

		track.updatedAt = now()
		track.artUpdatedAt = track.updatedAt

		trackRepository.save(track)
	}

	// It's important to rip the album art out PRIOR to running the song
	// through FFmpeg to be converted to an .ogg. If you don't, you will
	// get the error "Cannot find comment block (no vorbiscomment header)"
	private fun ripAndSaveAlbumArt(file: File): File? {
		val image = fileMetadataService.removeAlbumArtFromFile(file)
		return if (image != null) {
			val tmpImageName = UUID.randomUUID().toString() + ".png"
			val outputFile = File(fileStorageLocation.toString() + tmpImageName)
			ImageIO.write(image, "png", outputFile)

			outputFile
		} else {
			null
		}
	}

	@Transactional
	fun convertAndSaveTrackForUser(
			trackDTO: GGImportTrackDTO,
			user: User,
			song: File,
			art: File?,
			originalFileName: String
	): Track {
		logger.info("Saving file $originalFileName for user ${user.name}")

		val oggFile = ffmpegService.convertTrack(song, AudioFormat.OGG)
		// iOS does not support OGG playback. So at least for now, we have to store everything in both formats...
		val mp3File = ffmpegService.convertTrack(song, AudioFormat.MP3)

		// If the track file already has data embedded, pull it out and use it if the user has not overridden it
		val trackData = fileMetadataService.extractTrackInfoFromFile(oggFile, originalFileName)

		val (albumArt, thumbnailArt) = art?.let {
			val albumArt = imageService.convertToStandardArtFile(it, ArtSize.LARGE, trackDTO.cropArtToSquare)
			val thumbnailArt = imageService.convertToStandardArtFile(it, ArtSize.SMALL, trackDTO.cropArtToSquare)
			albumArt to thumbnailArt
		} ?: null to null

		val track = Track(
				user = user,
				name = trackDTO.name ?: trackData.name ?: "",
				artist = trackDTO.artist ?: trackData.artist ?: "",
				featuring = trackDTO.featuring ?: "",
				album = trackDTO.album ?: trackData.album ?: "",
				trackNumber = trackDTO.trackNumber ?: trackData.trackNumber,
				length = trackData.length,
				releaseYear = trackDTO.releaseYear ?: trackData.releaseYear,
				genre = trackDTO.genre ?: trackData.genre ?: "",
				note = trackDTO.note ?: "",
				filesizeSongOgg = oggFile.length(),
				filesizeSongMp3 = mp3File.length(),
				filesizeArtPng = albumArt?.length() ?: 0,
				filesizeThumbnail64x64Png = thumbnailArt?.length() ?: 0,
				fileName = ""
		)

		// Save once to have its ID generated
		trackRepository.save(track)

		// Use the ID to derive the file name and save again
		// TODO delete filename as it is stupid? Should be a Long column that denotes which tracks share data
		track.fileName = "${track.id}.ogg"
		trackRepository.save(track)

		fileStorageService.storeSong(oggFile, track.id, AudioFormat.OGG)
		fileStorageService.storeSong(mp3File, track.id, AudioFormat.MP3)
		albumArt?.let { fileStorageService.storeAlbumArt(it, track.id, ArtSize.LARGE) }
		thumbnailArt?.let { fileStorageService.storeAlbumArt(it, track.id, ArtSize.SMALL) }

		// We have stored the file in S3, (or copied it to its final home)
		// We no longer need these files and can clean it up to save space
		oggFile.delete()
		mp3File.delete()
		albumArt?.delete()
		thumbnailArt?.delete()

		return track
	}

	@Transactional
	fun editVolume(track: Track, volume: Double) {
		editSong(
				track = track,
				fileTransformationHandler = { songFile ->
					ffmpegService.convertTrack(songFile, AudioFormat.OGG, volume)
				},
				returnValueHandler = {}
		)
	}

	@Transactional
	fun trimSong(track: Track, startTime: String?, duration: String?): Int {
		return editSong(
				track = track,
				fileTransformationHandler = { songFile ->
					ffmpegService.trimSong(songFile, startTime, duration)
				},
				returnValueHandler = { editedFile ->
					fileMetadataService.extractTrackInfoFromFile(editedFile).length
				}
		)
	}

	private fun<T> editSong(
			track: Track,
			fileTransformationHandler: (File) -> File,
			returnValueHandler: (File) -> T
	): T {
		renameSharedTracks(track)

		val oggFile = fileStorageService.loadSong(track, AudioFormat.OGG)

		val transformedSongFile = fileTransformationHandler(oggFile)

		fileStorageService.storeSong(transformedSongFile, track.id, AudioFormat.OGG)

		val returnValue = returnValueHandler(transformedSongFile)

		// The new file we save is always specific to this song, so name it with the song's ID as we do
		// with any song file names that aren't borrowed / shared
		val newFileName = "${track.id}.ogg"
		if (newFileName != track.fileName) {
			logger.info("Track ${track.id} is now using the filename $newFileName instead of ${track.fileName}")
			track.fileName = newFileName
		}

		// Force a new link to be generated to break the browser cache
		trackLinkRepository.forceExpireLinksByTrackId(track.id)

		// It might make more sense to download the mp3 and trim it instead of re-converting the OGG to mp3
		// Maybe less loss in quality? But hard to say. Would require experimentation. At least this way we
		// save ourselves a trip to s3 and only download a single file
		val mp3File = ffmpegService.convertTrack(oggFile, AudioFormat.MP3)
		fileStorageService.storeSong(mp3File, track.id, AudioFormat.MP3)

		// We've made some sort of edit to the actual music file, so there are some
		// properties we always want to update on the Track
		track.filesizeSongOgg = transformedSongFile.length()
		track.filesizeSongMp3 = mp3File.length()
		track.updatedAt = now()
		track.songUpdatedAt = track.updatedAt

		trackRepository.save(track)

		oggFile.delete()
		mp3File.delete()
		transformedSongFile.delete()

		return returnValue
	}

	// Other users could be sharing this track. We don't want to let another user edit the track for all users
	// Make sure all the tracks sharing the filename of this track are no longer using it
	private fun renameSharedTracks(track: Track) {
		// If the track we are trimming has a different ID than its current name, then we don't have to do anything.
		// We will be giving this track a new file using its ID as its name, and all existing shared tracks can continue
		// with their life as is
		if (track.fileName.withoutExtension().toLong() != track.id) {
			return
		}

		val otherTracksUsingFile = trackRepository.findAllByFileName(track.fileName)
				.filter { it.id != track.id }
		// Nothing is sharing this file name. We don't have to worry
		if (otherTracksUsingFile.isEmpty()) {
			return
		}

		// We have at least one other song sharing the file with this song. Pick the first song of the shared songs,
		// and use its ID as the basis for the new song shares.
		val idForNewName = otherTracksUsingFile.first().id

		// For each supported file format, copy it to give it a new name that won't be stomped out with the new trim
		listOf(AudioFormat.OGG, AudioFormat.MP3).forEach { audioExtension ->
			val newSharedFileName = idForNewName.toString() + audioExtension.extension
			try {
				fileStorageService.copySong(
						track.fileName.withNewExtension(audioExtension.extension),
						newSharedFileName,
						audioExtension
				)
			} catch (e: Exception) {
				if (audioExtension == AudioFormat.MP3) {
					logger.warn("Failed to migrate shared MP3! Maybe it doesn't exist yet?", e)
				} else {
					logger.error("Failed to migrate shared OGG!")
					throw e
				}
			}
		}

		// Now that we have a new file name, update our DB entities to point to the new one
		otherTracksUsingFile.forEach {
			// File name was created at a time when only .ogg existed. Kind of awkward, but just use the OGG name here
			// MP3 name is always derived by just dropping the extension and replacing it
			it.fileName = idForNewName.toString() + AudioFormat.OGG.extension
			trackRepository.save(it)
			// We are using a new file name, and our track link will still link to the old file name unless we force expire it
			trackLinkRepository.forceExpireLinksByTrackId(it.id)
		}
	}

	fun createTrackFileWithMetadata(trackId: Long, audioFormat: AudioFormat): File {
		logger.info("About to create a downloadable track for track ID: $trackId with format ${audioFormat.extension}")
		val track = trackRepository.get(trackId)

		if (track == null || (track.private && track.user != loadLoggedInUser())) {
			throw IllegalArgumentException("No track found by ID $trackId!")
		}

		val songFile = fileStorageService.loadSong(track, audioFormat)
		val songArtist = if (track.artist.isBlank()) "Unknown" else track.artist
		val songName = if (track.name.isBlank()) "Unknown" else track.name
		val newName = "$songArtist - $songName${audioFormat.extension}".withoutReservedFileSystemCharacters()

		val artworkFile = fileStorageService.loadAlbumArt(trackId, ArtSize.LARGE)

		logger.info("Creating temporary track with name $newName")
		val renamedFile = File("${songFile.parent}/$newName")
		songFile.renameTo(renamedFile)

		logger.info("Adding metadata to temporary track $newName")
		fileMetadataService.addMetadataToFile(renamedFile, track, artworkFile)

		return renamedFile
	}

	companion object {
        private val logger = logger()
    }
}
