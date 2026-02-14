package com.panda.ktorwebsocketmvvm.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.Closeable
import java.io.File

/**
 * Wrapper around [MediaRecorder] for recording voice messages as AAC/M4A.
 *
 * Records to a temp file in [Context.getCacheDir]. Recordings shorter than 500ms
 * are discarded to avoid accidental taps.
 *
 * @param context Application context used for cache directory and MediaRecorder (API 31+).
 */
class AudioRecorder(private val context: Context) : Closeable {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0

    /**
     * Starts audio recording.
     * @return `true` if recording started successfully, `false` on error.
     */
    fun startRecording(): Boolean {
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            recordingStartTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            false
        }
    }

    /**
     * Stops the current recording.
     * @return [RecordingResult] with the audio file and duration, or `null` if the
     *         recording was too short (< 500ms) or an error occurred.
     */
    fun stopRecording(): RecordingResult? {
        return try {
            val duration = System.currentTimeMillis() - recordingStartTime
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            val file = outputFile
            if (file != null && file.exists() && duration > 500) {
                RecordingResult(file, duration)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            null
        }
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    override fun close() {
        cleanup()
    }

    /**
     * Holds the result of a successful recording.
     * @property file       Temp M4A file containing the recorded audio.
     * @property durationMs Approximate recording duration in milliseconds.
     */
    data class RecordingResult(val file: File, val durationMs: Long)
}
