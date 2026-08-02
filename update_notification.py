import re

with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''        const val EXTRA_TRACK_PATH = "extra_track_path"''',
    '''        const val EXTRA_TRACK_PATH = "extra_track_path"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_POSITION = "extra_position"'''
)

content = content.replace(
    '''        fun updateNotification(
            context: Context,
            track: Track?,
            isPlaying: Boolean''',
    '''        fun updateNotification(
            context: Context,
            track: Track?,
            isPlaying: Boolean,
            positionMs: Long = 0L'''
)

content = content.replace(
    '''                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TRACK_PATH, track.path)''',
    '''                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TRACK_PATH, track.path)
                putExtra(EXTRA_DURATION, track.durationMs)
                putExtra(EXTRA_POSITION, positionMs)'''
)

content = content.replace(
    '''            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Track"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val trackPath = intent.getStringExtra(EXTRA_TRACK_PATH) ?: ""

                updateMediaSession(title, artist, isPlaying, trackPath)''',
    '''            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Track"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val trackPath = intent.getStringExtra(EXTRA_TRACK_PATH) ?: ""
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                val position = intent.getLongExtra(EXTRA_POSITION, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)

                updateMediaSession(title, artist, isPlaying, trackPath, duration, position)'''
)

content = content.replace(
    '''    private fun updateMediaSession(
        title: String,
        artist: String,
        isPlaying: Boolean,
        trackPath: String
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
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        session.setPlaybackState(playbackState)''',
    '''    private fun updateMediaSession(
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
        session.setPlaybackState(playbackState)'''
)

content = content.replace(
    '''        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)''',
    '''        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)'''
)


with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'w') as f:
    f.write(content)
