package com.panda.ktorwebsocketmvvm.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages short sound effects for message send/receive events.
 * Uses ToneGenerator for lightweight, dependency-free tones.
 */
class SoundEffectManager(private val context: Context) {

    @Volatile
    var enabled: Boolean = true

    private val toneGenerator by lazy {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
        } catch (_: Exception) {
            null
        }
    }

    fun playSendSound() {
        if (!enabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        } catch (_: Exception) { }
    }

    fun playReceiveSound() {
        if (!enabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) { }
    }

    fun release() {
        try { toneGenerator?.release() } catch (_: Exception) { }
    }
}
