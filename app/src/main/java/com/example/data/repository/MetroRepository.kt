package com.example.data.repository

import android.content.Context
import android.provider.MediaStore
import com.example.data.database.*
import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MetroRepository(
    private val context: Context,
    private val database: MetroDatabase
) {
    private val playlistDao = database.playlistDao()
    private val playlistTrackDao = database.playlistTrackDao()
    private val userSettingsDao = database.userSettingsDao()

    // 5 default procedural ambient synth tracks
    val defaultSynthTracks = listOf(
        Track(
            id = "synth_lumia",
            title = "Lumia Ambient Dream",
            artist = "Azune Synth",
            durationMs = 180000L,
            path = "synth_lumia",
            isSynth = true,
            synthType = "lumia",
            genre = "Ambient Synth",
            album = "Procedural Dreams"
        ),
        Track(
            id = "synth_retro",
            title = "Retro Arp",
            artist = "Eight Bit Board",
            durationMs = 180000L,
            path = "synth_retro",
            isSynth = true,
            synthType = "retro",
            genre = "Ambient Synth",
            album = "Procedural Dreams"
        ),
        Track(
            id = "synth_blue",
            title = "Windows Blue Chill",
            artist = "Aero Wave System",
            durationMs = 180000L,
            path = "synth_blue",
            isSynth = true,
            synthType = "blue",
            genre = "Ambient Synth",
            album = "Classic Core"
        ),
        Track(
            id = "synth_cortana",
            title = "Cortana's Harmony",
            artist = "Halo AI Orchestrator",
            durationMs = 180000L,
            path = "synth_cortana",
            isSynth = true,
            synthType = "cortana",
            genre = "Ambient Synth",
            album = "Halo Synth"
        ),
        Track(
            id = "synth_redmond",
            title = "Redmond Drive",
            artist = "Classic Grid Synthesizer",
            durationMs = 180000L,
            path = "synth_redmond",
            isSynth = true,
            synthType = "redmond",
            genre = "Ambient Synth",
            album = "Classic Core"
        )
    )

    // Flow of Settings
    val userSettingsFlow: Flow<UserSettingsEntity> = userSettingsDao.getUserSettingsFlow().map {
        it ?: UserSettingsEntity() // return default settings if null
    }

    // Flow of Playlists
    val playlistsFlow: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylistsFlow()

    // Get track listings in a playlist
    fun getPlaylistTracksFlow(playlistId: Int): Flow<List<Track>> {
        return playlistTrackDao.getTracksForPlaylistFlow(playlistId).map { entities ->
            entities.map {
                Track(
                    id = it.trackId,
                    title = it.title,
                    artist = it.artist,
                    durationMs = it.durationMs,
                    path = it.path,
                    isSynth = it.isSynth,
                    synthType = it.synthType
                )
            }
        }
    }

    // Query physical media store
    suspend fun queryLocalMusic(targetFolder: String = "All"): List<Track> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        val baseProjection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
        }
        val projection = baseProjection.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val genreCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val idStr = if (idCol != -1) cursor.getLong(idCol).toString() else ""
                    val titleStr = if (titleCol != -1) cursor.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val artistStr = if (artistCol != -1) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val albumStr = if (albumCol != -1) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val durVal = if (durationCol != -1) cursor.getLong(durationCol) else 180000L
                    val genreStr = if (genreCol != -1 && genreCol < cursor.columnCount) {
                        cursor.getString(genreCol) ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    val folderList = targetFolder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val matchesFolder = if (folderList.isEmpty() || folderList.contains("All")) {
                        true
                    } else {
                        folderList.any { folder ->
                            dataPath.contains("/$folder", ignoreCase = true)
                        }
                    }

                    if (dataPath.isNotEmpty() && idStr.isNotEmpty() && matchesFolder) {
                        list.add(
                            Track(
                                id = "local_$idStr",
                                title = titleStr,
                                artist = artistStr,
                                durationMs = durVal,
                                path = dataPath,
                                isSynth = false,
                                genre = genreStr,
                                album = albumStr
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // No test music mock tracks as requested. Return only real scanned tracks.
        list
    }

    // Core actions
    suspend fun saveSettings(settings: UserSettingsEntity) {
        userSettingsDao.saveUserSettings(settings)
    }

    suspend fun createPlaylist(name: String, desc: String, accentHex: String): Long {
        val playlist = PlaylistEntity(
            name = name,
            description = desc,
            accentHex = accentHex
        )
        return playlistDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(id: Int) {
        playlistDao.deletePlaylist(id)
        playlistTrackDao.clearPlaylistTracks(id)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, track: Track) {
        val entity = PlaylistTrackEntity(
            playlistId = playlistId,
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs,
            path = track.path,
            isSynth = track.isSynth,
            synthType = track.synthType
        )
        playlistTrackDao.insertTrack(entity)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        playlistTrackDao.deleteTrackFromPlaylist(playlistId, trackId)
    }
}
