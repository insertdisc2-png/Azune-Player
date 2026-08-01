package com.example.data.model

data class Track(
    val id: String,         // "local_uri" or "synth_name"
    val title: String,
    val artist: String,
    val durationMs: Long,
    val path: String,       // URI or synth identification key
    val isSynth: Boolean,   // Is it procedurally generated?
    val synthType: String = "", // e.g. "lumia", "retro", "blue", "cortana", "redmond"
    val isFavorite: Boolean = false,
    val genre: String = "Unknown",
    val album: String = "Unknown"
)
