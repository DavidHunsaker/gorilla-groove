package com.gorilla.gorillagroove.service

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.gorilla.gorillagroove.R
import com.gorilla.gorillagroove.repository.MainRepository
import com.gorilla.gorillagroove.util.Constants.NOTIFICATION_CHANNEL_ID
import com.gorilla.gorillagroove.util.Constants.NOTIFICATION_ID
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.gorilla.gorillagroove.database.GorillaDatabase
import com.gorilla.gorillagroove.service.GGLog.logDebug
import com.gorilla.gorillagroove.service.GGLog.logError
import kotlinx.coroutines.*

class MusicNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener,
    mainRepository: MainRepository
) {

    private val trackDao get() = GorillaDatabase.getDatabase().trackDao()

    private val notificationManager: PlayerNotificationManager
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


    init {
        val mediaController = MediaControllerCompat(context, sessionToken)
        notificationManager = PlayerNotificationManager.createWithNotificationChannel(
            context,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_channel_name,
            R.string.notification_channel_description,
            NOTIFICATION_ID,
            DescriptionAdapter(mediaController, mainRepository),
            notificationListener
        ).apply {
            setSmallIcon(R.drawable.ic_music)
            setMediaSessionToken(sessionToken)
            setUseNavigationActionsInCompactView(true)
        }
    }

    fun showNotification(player: Player) {
        notificationManager.setPlayer(player)
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    private inner class DescriptionAdapter(
        private val mediaController: MediaControllerCompat,
        private val mainRepository: MainRepository
    ) : PlayerNotificationManager.MediaDescriptionAdapter {

        var currentIconUri: Uri? = null
        var currentBitmap: Bitmap? = null

        override fun getCurrentContentTitle(player: Player): CharSequence {
            return mediaController.metadata.description.title.toString()
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            return mediaController.sessionActivity
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            return mediaController.metadata.description.subtitle.toString()
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {

            val iconUri = mediaController.metadata.description.iconUri

            return if (currentIconUri != iconUri || currentBitmap == null) {

                currentIconUri = iconUri
                serviceScope.launch {
                    currentBitmap = resolveUriAsBitmap(iconUri)
                    currentBitmap?.let { callback.onBitmap(it) }
                }
                BitmapFactory.decodeResource(context.resources, R.drawable.blue)
            } else {
                currentBitmap
            }
        }

        private suspend fun resolveUriAsBitmap(iconUri: Uri?): Bitmap? {
            return withContext(Dispatchers.IO) {
                val trackId = iconUri.toString().toLong()

                val track = trackDao.findById(trackId) ?: run {
                    logError("Could not find track by ID $trackId when finding album art!")
                    return@withContext null
                }

                // If this is 0, then it means there isn't any art contained on the server
                if (track.filesizeArt == 0) {
                    return@withContext null
                }

                val artLink = TrackCacheService.getCacheItemIfAvailable(track.id, CacheType.ART)?.let { cachedArtFile ->
                    logDebug("Loading cached album art")
                    Uri.fromFile(cachedArtFile)
                } ?: try {
                    mainRepository.getTrackLinks(trackId).albumArtLink
                } catch (e: Throwable) {
                    logError("Failed to fetch album art track links!")
                }

                // Block on downloading artwork.
                artLink?.let {
                    Glide.with(context).applyDefaultRequestOptions(glideOptions)
                        .asBitmap()
                        .load(it)
                        .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                        .get()
                }
            }
        }
    }
}

const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
    .fallback(R.drawable.blue)
    .diskCacheStrategy(DiskCacheStrategy.DATA)





















