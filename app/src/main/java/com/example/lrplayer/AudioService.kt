package com.example.lrplayer

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.lrplayer.PlaybackStateHelper.CHANNEL_ID
import com.example.lrplayer.PlaybackStateHelper.NOTIFICATION_ID
import java.util.concurrent.atomic.AtomicBoolean

class AudioService : Service() {

    private var mediaPlayerPrevAudioUri: Uri = Uri.EMPTY
    private val mediaPlayerCreated: AtomicBoolean = AtomicBoolean(false)
    private lateinit var mediaSession: MediaSession
    var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Create a MediaSession
        mediaSession = MediaSession(this, "AudioService").apply {
            val playbackState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    PlaybackState.STATE_PAUSED,
                    0L,
                    1.0f // playback speed
                )
                .build()
            setPlaybackState(playbackState)

            // 4. Set a callback to handle media button events
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    if (mediaPlayerCreated.get()) {
                        if(mediaPlayer?.isPlaying == true) {
                            // Ensure the volume percentage is within valid range
                            var volumePercentage = Math.max(0, Math.min(80, 100))

                            // Initialize ToneGenerator with the specified volume level
                            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volumePercentage)
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200) // 200ms duration
                            toneGenerator.release() // Release resources after use
                        } else {
                            mediaPlayer?.start()

                            val intent = Intent("audio_service_update")
                            intent.putExtra("MediaSessionPause", false) // Add data if needed
                            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
                        }
                    }
                    // Handle play
                    // E.g., mediaPlayer?.start()
                }

                override fun onPause() {
                    super.onPause()
                    if (mediaPlayerCreated.get()) {

                        val intent = Intent("audio_service_update")
                        intent.putExtra("MediaSessionPause", true) // Add data if needed
                        LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)

                        mediaPlayer?.pause()
                    }
                    // Handle pause
                    // E.g., mediaPlayer?.pause()
                }

                override fun onStop() {
                    super.onStop()
                    // Handle stop
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    mediaPlayer?.seekTo(pos.toInt())
                }
            })

            // 5. Make the session active (needed so it can receive media buttons)
            isActive = true
        }



    }

    private fun stopMyMediaPlayer() {
        PlaybackStateHelper.needInitFromSaved.set(true)
        mediaPlayerPrevAudioUri = Uri.EMPTY
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaPlayerCreated.set(false)


        PlaybackStateHelper.backgroundServiceStarted.set(false)
    }

    private fun stopMyForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE) // or stopForeground(false) to keep the notification
        PlaybackStateHelper.isForeground.set(false)
        stopSelf()
    }

    /**
     * Called every time startService() or startForegroundService() is invoked.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION") ?: ""
        val audioUri = intent?.getStringExtra("AUDIO_URI")?.toUri()
        // Get the forward time from the intent, defaulting to 10000 ms (10 seconds)
        val forwardTimeMs = intent?.getIntExtra("SEEK_TO_TIME_MS", 10000) ?: 10000
        val pauseToggle = intent?.getBooleanExtra("PAUSE_TOGGLE", false) ?: false
        val clearCache = intent?.getBooleanExtra("CLEAR_RECENT_FILES", false) ?: false

        // Start playback in the foreground
        startForegroundWithNotification()

        if (action == "PLAY") {

            if(clearCache) {
                if (mediaPlayerCreated.get()) {
                    stopMyMediaPlayer()
                }

            } else if (audioUri != null) {
                reloadMediaPlayer(audioUri, pauseToggle)

            }


        }


        if (mediaPlayer != null) {
            if(!PlaybackStateHelper.backgroundServiceStarted.get()) {
                throw RuntimeException("cannot happen!")
            }

            if(!mediaPlayerCreated.get()) {
                throw RuntimeException("cannot happen!")
            }

            if (action == "PAUSE") {
                // Stop playback and remove from foreground
                if (pauseToggle) {
                    mediaPlayer?.pause()
                } else {
                    mediaPlayer?.start()
                }
            } else if (action == "STOP") {

                stopMyMediaPlayer()
                stopMyForegroundService()

                return START_NOT_STICKY
            } else if (action == "SEEK_TO") {
                mediaPlayer?.let {
                    var newPosition = maxOf(0, forwardTimeMs)
                    newPosition = minOf(it.duration, newPosition)
                    it.seekTo(newPosition - 1000)
                }
            } else {
                if(!"PLAY".equals(action) && !"".equals(action)) {
                    throw RuntimeException("cannot happen! action: $action")
                }
            }
        }

        return START_STICKY
        // Not sticky means the service won't recreate itself if killed by the system
//        return START_NOT_STICKY
    }

    /**
     * Create a notification channel (required on Android O and higher)
     * and start this service in the foreground with a notification.
     */
    private fun startForegroundWithNotification() {
        if (!PlaybackStateHelper.isForeground.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Audio Playback")
                    .setContentText("Playing background audio...")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)        // so user knows it's always active
                    .build()

                // Start as foreground service
                startForeground(NOTIFICATION_ID, notification)
                PlaybackStateHelper.isForeground.set(true)
            } else {
                throw RuntimeException("not supported!")
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        mediaSession.release()
        stopMyMediaPlayer()
        stopMyForegroundService()

        //stopPlaybackTimeCallback() // Ensure callback is stopped when service is destroyed
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun reloadMediaPlayer(audioUri: Uri, pauseToggle: Boolean) {
        if (!mediaPlayerCreated.get()) {
            // Initialize the MediaPlayer with your audio resource
            // Adjust R.raw.yt_14_zdf_001 to your raw file
            mediaPlayer = MediaPlayer.create(this, audioUri)
            mediaPlayer?.isLooping = true

            mediaPlayerCreated.set(true)
            PlaybackStateHelper.needInitFromSaved.set(true)

            // If the music completes, stop the service
            mediaPlayer?.setOnCompletionListener {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
        }


        if (Uri.EMPTY.equals(mediaPlayerPrevAudioUri) || !audioUri.equals(mediaPlayerPrevAudioUri)) {
            mediaPlayerPrevAudioUri = audioUri

            mediaPlayer?.reset() // Reset the existing MediaPlayer
            try {
                mediaPlayer?.setDataSource(this, audioUri)
                mediaPlayer?.prepare()
                PlaybackStateHelper.needInitFromSaved.set(true)
            } catch (e: Exception) {
                Log.e("AudioService", "Error loading audio: ${e.message}")
                // Handle the error appropriately (e.g., show a message to the user)
            }

            if (!pauseToggle) {
                mediaPlayer?.start()
            }
            //startPlaybackTimeCallback() // Start the callback when playback starts
            PlaybackStateHelper.backgroundServiceStarted.set(true)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

    }
}