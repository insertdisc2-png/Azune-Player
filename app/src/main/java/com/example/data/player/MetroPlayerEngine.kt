package com.example.data.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.data.model.Track
import com.example.data.synth.MetroSynthEngine

class MetroPlayerEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val synthEngine = MetroSynthEngine()
    
    private var currentTrack: Track? = null
    private var isPrepared = false

    fun preload(track: Track) {
        currentTrack = track
        isPrepared = false
    }

    fun isPrepared(): Boolean = isPrepared
    private var onCompletionListener: (() -> Unit)? = null
    private var playbackSpeed: Float = 1.0f
    private var playbackPitch: Float = 1.0f

    fun play(track: Track) {
        stop()
        currentTrack = track

        if (track.isSynth) {
            synthEngine.startPlaying(track.synthType)
        } else {
            try {
                mediaPlayer = MediaPlayer().apply {
                    val file = java.io.File(track.path)
                    if (track.path.startsWith("/") && file.exists() && file.isFile) {
                        val fis = java.io.FileInputStream(file)
                        setDataSource(fis.fd)
                        fis.close()
                    } else {
                        setDataSource(context, Uri.parse(track.path))
                    }
                    prepare()
                    isPrepared = true
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            val params = playbackParams
                            params.speed = playbackSpeed
                            params.pitch = playbackPitch
                            playbackParams = params
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    start()
                    try {
                        startVisualizer(audioSessionId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    setOnCompletionListener {
                        onCompletionListener?.invoke()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isPrepared = false
                // If local playback fails, fallback or trigger completion to handle gracefully
                onCompletionListener?.invoke()
            }
        }
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        playbackSpeed = speed
        playbackPitch = pitch
        val player = mediaPlayer ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isPrepared) {
            try {
                val params = player.playbackParams
                params.speed = speed
                params.pitch = pitch
                player.playbackParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pause() {
        val track = currentTrack ?: return
        if (track.isSynth) {
            synthEngine.pausePlaying()
        } else {
            stopVisualizer()
            if (isPrepared) {
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resume() {
        val track = currentTrack ?: return
        if (track.isSynth) {
            synthEngine.resumePlaying(track.synthType)
        } else {
            if (isPrepared) {
                try {
                    mediaPlayer?.start()
                    mediaPlayer?.let { startVisualizer(it.audioSessionId) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                play(track)
            }
        }
    }

    fun stop() {
        synthEngine.stopPlaying()
        stopVisualizer()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPrepared = false
    }

    fun isPlaying(): Boolean {
        val track = currentTrack ?: return false
        return if (track.isSynth) {
            synthEngine.isPlaying()
        } else {
            try {
                mediaPlayer?.isPlaying == true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getDuration(): Long {
        val track = currentTrack ?: return 0L
        return if (track.isSynth) {
            synthEngine.getDurationMs()
        } else {
            if (isPrepared) {
                try {
                    mediaPlayer?.duration?.toLong() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            } else {
                track.durationMs
            }
        }
    }

    fun getCurrentPosition(): Long {
        val track = currentTrack ?: return 0L
        return if (track.isSynth) {
            synthEngine.getCurrentPositionMs()
        } else {
            if (isPrepared) {
                try {
                    mediaPlayer?.currentPosition?.toLong() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            } else {
                0L
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val track = currentTrack ?: return
        if (track.isSynth) {
            synthEngine.seekToMs(positionMs)
        } else {
            if (isPrepared) {
                try {
                    mediaPlayer?.seekTo(positionMs.toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    private var visualizer: android.media.audiofx.Visualizer? = null

    private fun startVisualizer(sessionId: Int) {
        if (sessionId == 0) return
        try {
            stopVisualizer()
            visualizer = android.media.audiofx.Visualizer(sessionId).apply {
                val ranges = android.media.audiofx.Visualizer.getCaptureSizeRange()
                captureSize = if (ranges != null && ranges.size >= 2) ranges[0] else 128
                setDataCaptureListener(
                    object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: android.media.audiofx.Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                val tempAmps = FloatArray(12) { 0.15f }
                                val segmentSize = waveform.size / 12
                                for (i in 0 until 12) {
                                    var sum = 0f
                                    val start = i * segmentSize
                                    val end = (start + segmentSize).coerceAtMost(waveform.size)
                                    for (j in start until end) {
                                        val value = kotlin.math.abs((waveform[j].toInt() and 0xFF) - 128)
                                        sum += value / 128f
                                    }
                                    val avg = if (end > start) sum / (end - start) else 0f
                                    tempAmps[i] = (0.12f + avg * 4.5f).coerceIn(0.12f, 0.98f)
                                }
                                MetroVisualizerState.setRealData(tempAmps)
                            }
                        }

                        override fun onFftDataCapture(
                            v: android.media.audiofx.Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && fft.size >= 24) {
                                val tempAmps = FloatArray(12) { 0.15f }
                                val numBins = fft.size / 2
                                val binsPerBand = (numBins / 12).coerceAtLeast(1)
                                for (i in 0 until 12) {
                                    var sumMag = 0f
                                    val startBin = i * binsPerBand
                                    val endBin = ((i + 1) * binsPerBand).coerceAtMost(numBins)
                                    for (bin in startBin until endBin) {
                                        val r = fft[2 * bin].toFloat()
                                        val im = fft[2 * bin + 1].toFloat()
                                        val mag = kotlin.math.sqrt(r * r + im * im)
                                        sumMag += mag
                                    }
                                    val avgMag = if (endBin > startBin) sumMag / (endBin - startBin) else 0f
                                    tempAmps[i] = (0.12f + avgMag * 0.05f).coerceIn(0.12f, 0.98f)
                                }
                                MetroVisualizerState.setRealData(tempAmps)
                            }
                        }
                    },
                    android.media.audiofx.Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
        MetroVisualizerState.clearRealData()
    }
}
