package com.panda.ktorwebsocketmvvm.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.Closeable
import java.io.File

/**
 * Plays Base64-encoded audio data using [MediaPlayer].
 *
 * Decodes the Base64 string to a temp file, then plays it.
 * Automatically cleans up temp files when playback finishes or [stop] is called.
 *
 * @param context Application context used for the cache directory.
 */
class AudioPlayer(private val context: Context) : Closeable {

    private var mediaPlayer: MediaPlayer? = null
    private var currentTempFile: File? = null

    /** Callback invoked when playback finishes naturally. */
    var onPlaybackCompleted: (() -> Unit)? = null

    /** Returns `true` if audio is currently playing. */
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    /**
     * Decodes [base64Audio] and starts playback. Stops any current playback first.
     * @param base64Audio Base64-encoded AAC/M4A audio bytes.
     */
    fun play(base64Audio: String) {
        stop()

        try {
            val bytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "playback_${System.currentTimeMillis()}.m4a")
            tempFile.writeBytes(bytes)
            currentTempFile = tempFile

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    stop()
                    onPlaybackCompleted?.invoke()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    /** Stops playback, releases the MediaPlayer, and deletes the temp file. */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null

        currentTempFile?.delete()
        currentTempFile = null
    }

    override fun close() {
        stop()
        onPlaybackCompleted = null
    }
}
