package com.opencode.android.feature.wakeword

import kotlin.math.abs
import kotlin.math.sqrt

class VoiceActivityDetector(
    private val energyThreshold: Float = DEFAULT_THRESHOLD,
    private val hangoverFrames: Int = DEFAULT_HANGOVER
) {
    private var silentFrames = 0

    fun isSpeech(samples: ShortArray): Boolean {
        val rms = computeRms(samples)
        return if (rms >= energyThreshold) {
            silentFrames = 0
            true
        } else {
            silentFrames++
            silentFrames <= hangoverFrames
        }
    }

    fun reset() {
        silentFrames = 0
    }

    private fun computeRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val normalized = s / 32768f
            sum += normalized.toDouble() * normalized.toDouble()
        }
        return sqrt(sum / samples.size).toFloat()
    }

    companion object {
        private const val DEFAULT_THRESHOLD = 0.01f
        private const val DEFAULT_HANGOVER = 15
    }
}
