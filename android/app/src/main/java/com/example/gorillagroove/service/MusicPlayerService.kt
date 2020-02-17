package com.example.gorillagroove.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.gorillagroove.R
import com.example.gorillagroove.activities.MediaPlayerAudioLossEvent
import com.example.gorillagroove.activities.MediaPlayerLoadedEvent
import com.example.gorillagroove.activities.MediaPlayerPauseEvent
import com.example.gorillagroove.activities.MediaPlayerStartSongEvent
import com.example.gorillagroove.activities.MediaPlayerTransientAudioLossEvent
import com.example.gorillagroove.activities.PlayNextSongEvent
import com.example.gorillagroove.activities.PlayPreviousSongEvent
import com.example.gorillagroove.activities.PlaylistActivity
import com.example.gorillagroove.client.authenticatedGetRequest
import com.example.gorillagroove.client.markListenedRequest
import com.example.gorillagroove.client.nowPlayingRequest
import com.example.gorillagroove.db.GroovinDB
import com.example.gorillagroove.db.repository.UserRepository
import com.example.gorillagroove.dto.PlaylistSongDTO
import com.example.gorillagroove.dto.TrackResponse
import com.example.gorillagroove.utils.URLs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

@RequiresApi(Build.VERSION_CODES.N)
private const val IMPORTANCE = NotificationManager.IMPORTANCE_LOW

private const val NOTIFICATION_CHANNEL_ID = "channel_id"
private const val CHANNEL_NAME = "Notification Channel"
private const val ACTION_PLAY = "com.example.gorillagroove.ACTION_PLAY"
private const val ACTION_PAUSE = "com.example.gorillagroove.ACTION_PAUSE"
private const val ACTION_NEXT = "com.example.gorillagroove.ACTION_NEXT"
private const val ACTION_PREVIOUS = "com.example.gorillagroove.ACTION_PREVIOUS"

private const val NOTIFY_ID = 1
private const val REQUEST_CODE_PLAY = 900
private const val REQUEST_CODE_PAUSE = 910
private const val REQUEST_CODE_NEXT = 920
private const val REQUEST_CODE_PREVIOUS = 930

class MusicPlayerService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener,
        CoroutineScope by MainScope() {
    private val player = MediaPlayer()
    private val musicBind: IBinder = MusicBinder()

    private var token = ""
    private var email = ""
    private var deviceId = ""
    private var songTitle = ""
    private var artist = ""
    private var shuffle = false
    private var hasAudioFocus = false
    private var paused = false
    private var playbackDelayed = false
    private var songPosition = 0
    private var lastRecordedTime = 0
    private var playCountPosition = 0
    private var playCountDuration = 0
    private var markedListened = false
    private var currentSongPosition = 0
    private var shuffledSongs: List<Int> = emptyList()
    private var nowPlayingCounter = 0

    private lateinit var userRepository: UserRepository
    private lateinit var songs: List<PlaylistSongDTO>
    private lateinit var audioManager: AudioManager
    private val mFocusLock = Object()

    override fun onCreate() {
        Log.i("MSP", "onCreate is called")
        super.onCreate()
        songPosition = 0
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initMusicPlayer()

        userRepository =
                UserRepository(GroovinDB.getDatabase(this@MusicPlayerService).userRepository())
        if (token.isBlank() || deviceId.isBlank() || email.isBlank()) {
            launch {
                withContext(Dispatchers.IO) {
                    userInformation()
                }
            }
        }
    }

    private fun userInformation() {
        try {
            val user = userRepository.lastLoggedInUser()
            if (user != null) {
                token = user.token!!
                deviceId = user.deviceId!!
                email = user.email
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "User not found!: $e")
        }
    }

    private fun initMusicPlayer() {
        Log.i("MSP", "Initializing Media Player")
        player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        player.setAudioStreamType(AudioManager.STREAM_MUSIC) // We are supporting API 19, and thus need this deprecated method
        player.setOnPreparedListener(this)
        player.setOnCompletionListener(this)
        player.setOnErrorListener(this)
    }

    fun setSongList(songList: List<PlaylistSongDTO>) {
        songs = songList
    }

    fun setSong(songIndex: Int) {
        songPosition = songIndex
        currentSongPosition = songIndex
        if (shuffle) {
            songPosition = shuffledSongs.indexOf(songIndex)
        }
    }

    fun getPosition(): Int {
        val currentTime = System.currentTimeMillis().toInt()
        val elapsedPosition = currentTime - lastRecordedTime
        lastRecordedTime = currentTime
        playCountPosition += elapsedPosition // Milliseconds

        if (player.isPlaying) nowPlayingCounter += elapsedPosition
        if (nowPlayingCounter >= 15000) { // about 15 seconds
            sendNowPlayingRequest(deviceId, getCurrentTrackId())
            nowPlayingCounter = 0
        }
        if (!markedListened && playCountDuration > 0 && playCountPosition >= playCountDuration) {
            markListened(playCountPosition, player.currentPosition, playCountDuration, deviceId)
            markedListened = true
        }
        return player.currentPosition
    }

    private fun sendNowPlayingRequest(deviceId: String, currentTrackId: Long?) {
        Log.d("MusicPlayerService", "Sending now playing request for $currentTrackId")
        launch {
            withContext(Dispatchers.IO) {
                nowPlayingRequest(
                        URLs.NOW_PLAYING,
                        currentTrackId,
                        token,
                        deviceId
                )
            }
        }
    }


    fun getDuration(): Int {
        if (playCountDuration == 0) playCountDuration = (player.duration * 0.6).toInt()
        return player.duration
    }

    fun isPlaying(): Boolean {
        return player.isPlaying
    }

    fun pausePlayer() {
        sendNowPlayingRequest(deviceId, null)
        player.pause()
    }

    fun start() {
        player.start()
        lastRecordedTime = System.currentTimeMillis().toInt()
        sendNowPlayingRequest(deviceId, getCurrentTrackId())
    }

    private fun getCurrentTrackId(): Long {
        return when (shuffle) {
            true -> songs[currentSongPosition].track.id
            false -> songs[songPosition].track.id
        }
    }

    fun requestAudioFocus() {
        val requestAudioFocus: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mPlaybackAttributes = AudioAttributes.Builder()
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mPlaybackAttributes)
                    .setOnAudioFocusChangeListener(this).build()
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            audioManager.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            )
        }
        synchronized(mFocusLock) {
            when (requestAudioFocus) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    playbackDelayed = false
                }
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    playbackDelayed = false
                    start()
                    EventBus.getDefault()
                            .post(MediaPlayerLoadedEvent("Media Player Loaded, now Showing"))
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    playbackDelayed = true
                }
            }
        }
    }

    fun seek(position: Int) {
        player.seekTo(position)
        lastRecordedTime = System.currentTimeMillis().toInt()
    }

    fun getBufferPercentage(): Int {
        return (player.currentPosition * 100) / player.duration
    }

    fun getAudioSessionId(): Int {
        return player.audioSessionId
    }

    fun playPrevious() {
        songPosition -= 1
        if (songPosition < 0) songPosition = songs.size - 1

        playSong()
    }

    fun playNext() {
        songPosition += 1
        if (songPosition >= songs.size) songPosition = 0
        Log.i("MusicPlayerService", "Current songPosition is now=$songPosition")
        playSong()
    }

    inner class MusicBinder : Binder() {

        fun getService(): MusicPlayerService {
            return this@MusicPlayerService
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (!action.isNullOrBlank()) {
                when (action) {
                    ACTION_PLAY -> EventBus.getDefault().post(MediaPlayerStartSongEvent("Resuming Play"))
                    ACTION_PAUSE -> EventBus.getDefault().post(MediaPlayerPauseEvent("Pausing Media"))
                    ACTION_NEXT -> EventBus.getDefault().post(PlayNextSongEvent("Playing Next Song"))
                    ACTION_PREVIOUS -> EventBus.getDefault().post(PlayPreviousSongEvent("Playing Previous Song"))
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onPrepared(mp: MediaPlayer) {
        requestAudioFocus()

        val notificationIntent = Intent(
                applicationContext,
                PlaylistActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playSongIntent = Intent(ACTION_PLAY)
        val pendingPlaySongIntent = PendingIntent.getService(
                applicationContext,
                REQUEST_CODE_PLAY,
                playSongIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseSongIntent = Intent(ACTION_PAUSE)
        val pendingPauseSongIntent = PendingIntent.getService(
                applicationContext,
                REQUEST_CODE_PAUSE,
                pauseSongIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )


        val nextSongIntent = Intent(ACTION_NEXT)
        val pendingNextSongIntent = PendingIntent.getService(
                applicationContext,
                REQUEST_CODE_NEXT,
                nextSongIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousSongIntent = Intent(ACTION_PREVIOUS)
        val pendingPreviousSongIntent = PendingIntent.getService(
                applicationContext,
                REQUEST_CODE_PREVIOUS,
                previousSongIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                    NotificationChannel(NOTIFICATION_CHANNEL_ID, CHANNEL_NAME, IMPORTANCE)
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val notificationCompat = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_media_pause, "pause", pendingPauseSongIntent)
                    .addAction(android.R.drawable.ic_media_play, "play", pendingPlaySongIntent)
                    .addAction(android.R.drawable.ic_media_next, "next", pendingNextSongIntent)
                    .addAction(android.R.drawable.ic_media_previous, "previous", pendingPreviousSongIntent)
                    .setContentTitle("Gorilla Groove")
                    .setContentText("$songTitle - $artist")
                    .setSmallIcon(R.drawable.logo)
                    .setTicker("$songTitle - $artist")
                    .setOngoing(true)

            startForeground(NOTIFY_ID, notificationCompat.build())
        } else {
            val notificationCompat = Notification.Builder(applicationContext)
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_media_pause, "pause", pendingPauseSongIntent)
                    .addAction(android.R.drawable.ic_media_play, "play", pendingPlaySongIntent)
                    .addAction(android.R.drawable.ic_media_next, "next", pendingNextSongIntent)
                    .setContentTitle("Gorilla Groove")
                    .setContentText("$songTitle - $artist")
                    .setSmallIcon(R.drawable.logo)
                    .setTicker("$songTitle - $artist")
                    .setOngoing(true)

            startForeground(NOTIFY_ID, notificationCompat.build())
        }
        EventBus.getDefault().post(MediaPlayerLoadedEvent("Media Player Loaded, now Showing"))
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        clearPlayCountInfo()
        mp!!.reset()
        return false
    }

    override fun onCompletion(mp: MediaPlayer?) {
        EventBus.getDefault().post(PlayNextSongEvent("Resetting Music Player"))
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent != null) {
            if (intent.hasExtra("email")) email = intent.getStringExtra("email")
            if (intent.hasExtra("token")) token = intent.getStringExtra("token")
            if (intent.hasExtra("deviceId")) deviceId = intent.getStringExtra("deviceId")
        }
        return musicBind
    }

    override fun onUnbind(intent: Intent?): Boolean {
        player.stop()
        player.release()
        return false
    }

    override fun onDestroy() {
        sendNowPlayingRequest(deviceId, null)
        stopForeground(true)
    }

    fun setShuffle(): Boolean {
        shuffle = !shuffle
        when (shuffle) {
            true -> {
                shuffledSongs = songs.indices.toList().shuffled()
                Log.i(
                        "Shuffling",
                        "Shuffled list is: $shuffledSongs\n Songs list is ${songs.indices.toList()}"
                )
            }
            false -> songPosition = currentSongPosition
        }
        Log.i("Shuffle Alert!", "Shuffle is now set to $shuffle")
        return shuffle
    }

    fun playSong() {
        player.reset()
        clearPlayCountInfo()
        val song =
                when (shuffle) {
                    true -> {
                        currentSongPosition = shuffledSongs[songPosition]
                        songs[currentSongPosition]
                    }
                    false -> songs[songPosition]
                }
        songTitle = song.track.name.toString()
        artist = song.track.artist.toString()

        val trackResponse = getSongStreamInfo(song.track.id)

        try {
            player.setDataSource(trackResponse.songLink)
            player.prepare()
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error setting data source: $e")
        }
    }

    private fun clearPlayCountInfo() {
        playCountPosition = 0
        playCountDuration = 0
        markedListened = false
    }

    private fun getSongStreamInfo(trackId: Long): TrackResponse {
        Log.d("MusicPlayerService", "Getting song info for track=$trackId with token=$token")

        val response = authenticatedGetRequest(URLs.TRACK + "$trackId", token)

        return TrackResponse(response["songLink"].toString(), response["albumArtLink"].toString())
    }

    private fun markListened(
            playCountPosition: Int,
            playerCurrentPosition: Int,
            playCountDuration: Int,
            deviceId: String
    ) {
        val trackId = getCurrentTrackId()
        Log.d(
                "MusicPlayerService",
                "Marking track=$trackId as listened with:\nplayCountPosition=$playCountPosition\nplayerCurrentPosition=$playerCurrentPosition\nplayCountDuration=$playCountDuration"
        )
        launch {
            withContext(Dispatchers.IO) {
                markListenedRequest(
                        URLs.MARK_LISTENED,
                        trackId,
                        token,
                        deviceId
                )
            }
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("MusicPlayerService", "Audio Focus Gained")
                hasAudioFocus = true
                if (!paused) {
                    player.start()
                    EventBus.getDefault()
                            .post(MediaPlayerLoadedEvent("Media Player Loaded, now Showing"))
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("MusicPlayerService", "Audio Focus Loss Transient")
                hasAudioFocus = false
                paused = true
                EventBus.getDefault()
                        .post(MediaPlayerTransientAudioLossEvent("Transient Audiofocus Loss, Pausing Playback"))
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("MusicPlayerService", "AudioFocusLoss")
                hasAudioFocus = false
                paused = true
                EventBus.getDefault()
                        .post(MediaPlayerAudioLossEvent("Audiofocus Loss, Stopping Playback"))
            }
        }
    }
}