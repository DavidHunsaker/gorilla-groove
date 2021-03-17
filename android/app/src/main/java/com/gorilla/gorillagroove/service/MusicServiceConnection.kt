package com.gorilla.gorillagroove.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.MutableLiveData

class MusicServiceConnection(context: Context, serviceComponent: ComponentName) {

    private val TAG = "AppDebug : MusicServiceConnection"

    val isConnected = MutableLiveData<Boolean>()
        .apply { postValue(false) }

    val networkFailure = MutableLiveData<Boolean>()
        .apply { postValue(false) }

    val rootMediaId: String
        get() = mediaBrowser.root

    val playbackState = MutableLiveData<PlaybackStateCompat>()
        .apply { postValue(EMPTY_PLAYBACK_STATE) }

    val repeatState = MutableLiveData<Int>()
        .apply { postValue(PlaybackStateCompat.REPEAT_MODE_NONE) }

    val nowPlaying = MutableLiveData<MediaMetadataCompat>()
        .apply { postValue(NOTHING_PLAYING) }

    val transportControls: MediaControllerCompat.TransportControls
        get() = mediaController.transportControls


    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)
    private val mediaBrowser = MediaBrowserCompat(
        context,
        serviceComponent,
        mediaBrowserConnectionCallback, null
    ).apply {
        connect()
    }

    private lateinit var mediaController: MediaControllerCompat


    private inner class MediaBrowserConnectionCallback(private val context: Context) :
        MediaBrowserCompat.ConnectionCallback() {

        override fun onConnected() {
            // Get a MediaController for the MediaSession.
            mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(MediaControllerCallback())
            }

            isConnected.postValue(true)
        }

        override fun onConnectionSuspended() {
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            isConnected.postValue(false)
        }
    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatState.postValue(repeatMode)
        }



        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            nowPlaying.postValue(
                if (metadata?.id == null) {
                    NOTHING_PLAYING
                } else {
                    metadata
                }
            )
        }
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            when (event) {
                NETWORK_FAILURE -> networkFailure.postValue(true)
            }
        }

        override fun onSessionDestroyed() {
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }

    companion object {
        @Volatile
        private var instance: MusicServiceConnection? = null

        fun getInstance(context: Context, serviceComponent: ComponentName) =
            instance ?: synchronized(this) {
                instance ?: MusicServiceConnection(context, serviceComponent)
                    .also { instance = it }
            }
    }

}

@Suppress("PropertyName")
val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = PlaybackStateCompat.Builder()
    .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
    .build()

@Suppress("PropertyName")
val NOTHING_PLAYING: MediaMetadataCompat = MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
    .build()

inline val MediaMetadataCompat.id: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)

const val NETWORK_FAILURE = "com.gorilla.ggmobileredux.service.NETWORK_FAILURE"