package com.example.data.player

import java.util.concurrent.atomic.AtomicBoolean

object MetroVisualizerState {
    @Volatile
    var currentAmplitudes = FloatArray(12) { 0.15f }

    private val isUsingRealData = AtomicBoolean(false)

    fun setRealData(amplitudes: FloatArray) {
        currentAmplitudes = amplitudes
        isUsingRealData.set(true)
    }

    fun hasRealData(): Boolean {
        return isUsingRealData.get()
    }

    fun clearRealData() {
        isUsingRealData.set(false)
        for (i in currentAmplitudes.indices) {
            currentAmplitudes[i] = 0.15f
        }
    }
}
