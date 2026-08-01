package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Playlist Metadata Entity
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val accentHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Playlist Track Connector Entity
@Entity(
    tableName = "playlist_tracks",
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val path: String,
    val isSynth: Boolean,
    val synthType: String
)

// User Custom Looks Settings Entity
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val accentColorHex: String = "#0078D7", // Default WP Cobalt Blue
    val fontFamily: String = "Inter",      // "Inter", "Monospace", "Slab", "Aesthetic"
    val backgroundTransparency: Float = 0.3f, // 0.0 (opaque) to 0.9 (translucent)
    val backgroundStyle: String = "grid",   // "solid", "grid", "retro-tiles"
    val favoriteTrackIds: String = "",       // Comma-separated list of favorite track IDs
    val themeMode: String = "dark",         // "dark", "light", "system"
    val targetMusicFolder: String = "All",   // "All", "Music", "Downloads", etc. (comma separated for custom multifolders)
    val welcomeCompleted: Boolean = false,
    val cornerCoverArt: String = "sharp",   // "sharp", "rounded", "circle"
    val coverBackgroundStyle: String = "solid", // "solid" (theme base), "cover-color" (vibrant tone), "cover-color-muted"
    val appBackgroundImage: String = "",     // Uri or path of background image
    val appBackgroundBlur: Float = 4f,      // Blur radius (1f to 25f)
    val appBackgroundOpacity: Float = 0.3f,   // Opacity/clearness intensity (0f to 1f)
    val previewLyrics: Boolean = true,
    val lyricsFontFamily: String = "Inter",
    val lyricsSpacing: String = "Normal",    // "Tight", "Normal", "Spacious"
    val lyricsAlignment: String = "Left",  // "Left", "Center", "Right", "Follow"
    val lyricsFontSize: String = "Medium",   // "Small", "Medium", "Large"
    val playerBackgroundStyle: String = "theme", // "theme", "dark", "light", "cover"
    val playerBackgroundIntensity: Float = 0.5f, // Float (0.0 to 1.0)
    val pinnedTrackIds: String = "", // comma-separated track IDs
    val showPlaceholderSongs: Boolean = false,
    val enableCoverArt: Boolean = true,
    val coverArtResolution: String = "original", // "optimized", "original", or "low", "medium"
    val enableListCoverArt: Boolean = true,
    val rememberLastPlayed: Boolean = true,
    val lastPlayedTrackId: String = "",
    val lastPlayedPositionMs: Long = 0L,
    val tileOrder: String = "now_playing,pinned_tracks,my_music,playlists,favorites,artists,albums",
    val tileSpans: String = "now_playing:2,pinned_tracks:2,my_music:1,playlists:1,favorites:1,artists:1,albums:1",
    val musicSortBy: String = "A-Z",
    val musicSortAscending: Boolean = true,
    val visibleTiles: String = "now_playing,pinned_tracks,my_music,playlists,favorites,artists,albums",
    val coverArtBorderThickness: String = "off", // "off", "normal", "bold", "super-bold"
    val artistSeparators: String = "/ ; , ; feat. ; ft. ; & ; and",
    val nowPlayingTileStyle: String = "wave" // "wave" or "cover"
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Int)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
}

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY id ASC")
    fun getTracksForPlaylistFlow(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE id = :id")
    suspend fun deleteTrack(id: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrackFromPlaylist(playlistId: Int, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Int)
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettingsFlow(): Flow<UserSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserSettings(settings: UserSettingsEntity)
}

@Database(
    entities = [PlaylistEntity::class, PlaylistTrackEntity::class, UserSettingsEntity::class],
    version = 9,
    exportSchema = false
)
abstract class MetroDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun userSettingsDao(): UserSettingsDao
}
