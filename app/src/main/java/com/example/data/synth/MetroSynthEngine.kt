package com.example.data.synth

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class MetroSynthEngine {
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val SAMPLE_RATE = 44100
    private var isPlaying = false
    private var currentSynthType = "lumia"

    // Multi-voice tracker for arpeggiations and chord sweeps
    private var tickCount = 0L
    private var currentPositionMs = 0L

    fun startPlaying(synthType: String) {
        startPlayingInternal(synthType, resetState = true)
    }

    fun resumePlaying(synthType: String) {
        startPlayingInternal(synthType, resetState = false)
    }

    private fun startPlayingInternal(synthType: String, resetState: Boolean) {
        stopPlayingInternal(keepState = !resetState)
        currentSynthType = synthType
        isPlaying = true
        if (resetState) {
            tickCount = 0L
            currentPositionMs = 0L
        }

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Make buffer size generous but responsive
        val bufferSize = (minBufSize * 2).coerceAtLeast(4096)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        playJob = scope.launch {
            val shortBuffer = ShortArray(1024)
            
            // Frequencies for our musical templates
            // C4=261.63, E4=329.63, G4=392.00, B4=493.88, D5=587.33, A4=440.00, F4=349.23, G4=392.00 etc.
            val chordProgression1 = listOf(
                listOf(261.63, 329.63, 392.00, 493.88), // Cmaj7
                listOf(220.00, 261.63, 329.63, 392.00), // Am7
                listOf(349.23, 440.00, 523.25, 659.25), // Fmaj7
                listOf(293.66, 349.23, 440.00, 587.33)  // Dm7
            )

            val chordProgressionRetro = listOf(
                listOf(130.81, 164.81, 196.00), // C3
                listOf(110.00, 130.81, 164.81), // A2
                listOf(146.83, 174.61, 220.00), // D3
                listOf(196.00, 246.94, 293.66)  // G3
            )

            // Phase tracking variables
            var phases = DoubleArray(6) { 0.0 }

            while (isActive && isPlaying) {
                // Determine current note/chord timing
                // 1 tick is 1024 samples @ 44.1kHz = ~23.2ms
                val tempoTicks = 64 // ~1.5s per chord
                val currentChordIndex = ((tickCount / tempoTicks) % 4).toInt()
                
                val chords = when (currentSynthType) {
                    "retro" -> chordProgressionRetro[currentChordIndex]
                    "blue" -> listOf(146.83, 220.00, 293.66, 392.00) // Beautiful open Dsus pad
                    "cortana" -> listOf(220.00, 329.63, 440.00, 493.88) // Futuristic minor suspension
                    "redmond" -> listOf(130.81, 261.63, 392.00, 587.33) // Wide spectrum open C
                    else -> chordProgression1[currentChordIndex] // Lumia Ambient
                }

                // Generates the arpeggio wave dynamically for "retro"
                val retroArpNoteIndex = ((tickCount / 8) % 4).toInt() // fast note changes
                val retroArpFreq = if (retroArpNoteIndex < chords.size) chords[retroArpNoteIndex] * 2.0 else chords[0] * 2.0

                for (i in shortBuffer.indices) {
                    var sampleVal = 0.0
                    
                    if (currentSynthType == "retro") {
                        // 8-bit pulse wave (square)
                        val freq = retroArpFreq
                        val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
                        phases[0] = (phases[0] + phaseStep) % (2.0 * PI)
                        
                        // Square wave generation with simple ADSR decay
                        val sq = if (phases[0] < PI) 0.15 else -0.15
                        val tickInArp = tickCount % 8
                        val decay = (8.0 - tickInArp) / 8.0 // quick pluck decay
                        sampleVal += sq * decay

                        // Add a low bass drone
                        val bassFreq = chords[0] / 2.0
                        val bassPhaseStep = 2.0 * PI * bassFreq / SAMPLE_RATE
                        phases[1] = (phases[1] + bassPhaseStep) % (2.0 * PI)
                        sampleVal += if (phases[1] < PI) 0.1 else -0.1
                    } else if (currentSynthType == "blue") {
                        // Thick analog-feeling sawtooth / triangle pads with slow volume sweeping
                        val scale = sin(tickCount.toDouble() * 0.01) * 0.1 + 0.15 // tremolo
                        for (j in chords.indices) {
                            val freq = chords[j]
                            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
                            phases[j] = (phases[j] + phaseStep) % (2.0 * PI)
                            // Triangle wave
                            val p = phases[j] / (2.0 * PI)
                            val tri = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                            sampleVal += tri * (0.08 / chords.size) * scale
                        }
                    } else if (currentSynthType == "cortana") {
                        // Metallic bells / futuristic ring modulator sound
                        val scale = sin(tickCount.toDouble() * 0.02) * 0.08 + 0.12
                        for (j in chords.indices) {
                            val freq = chords[j] * (if (j == 3) 2.0 else 1.0)
                            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
                            phases[j] = (phases[j] + phaseStep) % (2.0 * PI)
                            sampleVal += sin(phases[j]) * (0.15 / chords.size) * scale
                        }
                    } else if (currentSynthType == "redmond") {
                        // Heavy, rich synth drone with filtering
                        val drive = sin(tickCount.toDouble() * 0.005) * 0.15 + 0.15
                        for (j in chords.indices) {
                            val freq = chords[j]
                            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
                            phases[j] = (phases[j] + phaseStep) % (2.0 * PI)
                            // Warm Sine + 1st Harmonic
                            val sineVal = sin(phases[j]) + 0.4 * sin(phases[j] * 2.0)
                            sampleVal += sineVal * (0.1 / chords.size) * drive
                        }
                    } else {
                        // Default Lumia Ambient Swell: overlapping sine waves with soft attack
                        val chordTick = tickCount % tempoTicks
                        val attackEnd = tempoTicks / 4
                        val decayStart = tempoTicks * 3 / 4
                        
                        val volumeEnvelope = when {
                            chordTick < attackEnd -> chordTick.toDouble() / attackEnd
                            chordTick > decayStart -> (tempoTicks - chordTick).toDouble() / (tempoTicks - decayStart)
                            else -> 1.0
                        }

                        for (j in chords.indices) {
                            val freq = chords[j]
                            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
                            phases[j] = (phases[j] + phaseStep) % (2.0 * PI)
                            sampleVal += sin(phases[j]) * (0.2 / chords.size) * volumeEnvelope
                        }
                    }

                    // Clip sample safely to Short range
                    val finalShort = (sampleVal * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    shortBuffer[i] = finalShort
                }

                val tempAmps = FloatArray(12) { 0.15f }
                val segmentSize = shortBuffer.size / 12
                for (step in 0 until 12) {
                    var sum = 0f
                    val startIdx = step * segmentSize
                    for (idx in startIdx until (startIdx + segmentSize)) {
                        sum += kotlin.math.abs(shortBuffer[idx].toFloat()) / Short.MAX_VALUE.toFloat()
                    }
                    val avg = if (segmentSize > 0) sum / segmentSize else 0f
                    tempAmps[step] = (avg * 5.0f).coerceIn(0.12f, 0.98f)
                }
                com.example.data.player.MetroVisualizerState.setRealData(tempAmps)

                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
                
                tickCount++
                currentPositionMs += (1024 * 1000L) / SAMPLE_RATE
            }
        }
    }

    fun stopPlaying() {
        stopPlayingInternal(keepState = false)
    }

    fun pausePlaying() {
        stopPlayingInternal(keepState = true)
    }

    private fun stopPlayingInternal(keepState: Boolean) {
        isPlaying = false
        playJob?.cancel()
        playJob = null
        com.example.data.player.MetroVisualizerState.clearRealData()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        if (!keepState) {
            tickCount = 0L
            currentPositionMs = 0L
        }
    }

    fun seekToMs(positionMs: Long) {
        currentPositionMs = positionMs
        tickCount = (positionMs * SAMPLE_RATE) / (1024L * 1000L)
    }

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentPositionMs(): Long = currentPositionMs

    fun getDurationMs(): Long = 180000L // 3 minutes simulated loop duration
}
