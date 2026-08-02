package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.Track

class MusicNotificationService : Service() {

    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            mediaSession = MediaSessionCompat(this, "AzuneMediaSession").apply {
                setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        onNotificationAction?.invoke(ACTION_PLAY)
                    }

                    override fun onPause() {
                        onNotificationAction?.invoke(ACTION_PAUSE)
                    }

                    override fun onSkipToNext() {
                        onNotificationAction?.invoke(ACTION_NEXT)
                    }

                    override fun onSkipToPrevious() {
                        onNotificationAction?.invoke(ACTION_PREVIOUS)
                    }

                    override fun onStop() {
                        onNotificationAction?.invoke(ACTION_STOP)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Track"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val trackPath = intent.getStringExtra(EXTRA_TRACK_PATH) ?: ""
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                val position = intent.getLongExtra(EXTRA_POSITION, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)

                updateMediaSession(title, artist, isPlaying, trackPath, duration, position)
                val notification = buildNotification(title, artist, isPlaying, trackPath)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ACTION_PLAY -> {
                onNotificationAction?.invoke(ACTION_PLAY)
            }
            ACTION_PAUSE -> {
                onNotificationAction?.invoke(ACTION_PAUSE)
            }
            ACTION_NEXT -> {
                onNotificationAction?.invoke(ACTION_NEXT)
            }
            ACTION_PREVIOUS -> {
                onNotificationAction?.invoke(ACTION_PREVIOUS)
            }
            ACTION_STOP -> {
                mediaSession?.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun updateMediaSession(
        title: String,
        artist: String,
        isPlaying: Boolean,
        trackPath: String,
        duration: Long,
        position: Long
    ) {
        val session = mediaSession ?: return

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, if (isPlaying) 1.0f else 0.0f)
            .build()
        session.setPlaybackState(playbackState)

        var albumArtBitmap = try {
            if (trackPath.isNotEmpty() && !trackPath.startsWith("synth://")) {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(trackPath)
                val artBytes = mmr.embeddedPicture
                mmr.release()
                if (artBytes != null) {
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (albumArtBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
        }
        session.setMetadata(metadataBuilder.build())
        session.isActive = true
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        trackPath: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MusicNotificationService::class.java).apply {
            this.action = ACTION_PREVIOUS
        }
        val pendingPrev = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicNotificationService::class.java).apply {
            this.action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val pendingPlayPause = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicNotificationService::class.java).apply {
            this.action = ACTION_NEXT
        }
        val pendingNext = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicNotificationService::class.java).apply {
            this.action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        var albumArtBitmap = try {
            if (trackPath.isNotEmpty() && !trackPath.startsWith("synth://")) {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(trackPath)
                val artBytes = mmr.embeddedPicture
                mmr.release()
                if (artBytes != null) {
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(albumArtBitmap)
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pendingPrev)
            .addAction(playPauseIcon, playPauseTitle, pendingPlayPause)
            .addAction(android.R.drawable.ic_media_next, "Next", pendingNext)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)

        mediaSession?.let { session ->
            builder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for active music playback"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_PLAY = "com.example.service.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.service.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_TRACK_PATH = "extra_track_path"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_POSITION = "extra_position"

        var onNotificationAction: ((String) -> Unit)? = null

        fun updateNotification(
            context: Context,
            track: Track?,
            isPlaying: Boolean,
            positionMs: Long = 0L
        ) {
            if (track == null) {
                stopNotification(context)
                return
            }

            val intent = Intent(context, MusicNotificationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, track.title)
                putExtra(EXTRA_ARTIST, track.artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TRACK_PATH, track.path)
                putExtra(EXTRA_DURATION, track.durationMs)
                putExtra(EXTRA_POSITION, positionMs)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopNotification(context: Context) {
            val intent = Intent(context, MusicNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
