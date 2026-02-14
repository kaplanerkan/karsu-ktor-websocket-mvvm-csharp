package com.panda.ktorwebsocketmvvm.ui.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panda.ktorwebsocketmvvm.data.audio.AudioPlayer
import com.panda.ktorwebsocketmvvm.data.audio.AudioRecorder
import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.model.ErrorType
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.min

/**
 * ViewModel for the chat screen. Manages connection lifecycle, text and voice messaging,
 * audio recording/playback state, and automatic reconnection on connection loss.
 *
 * Injected via Koin with [ChatRepository], [AudioRecorder], and [AudioPlayer].
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val _uiConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _uiConnectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile
    private var currentUsername: String = "karsu"

    private var userDisconnected = false
    private var reconnectJob: Job? = null
    private var lastHost: String = ""
    private var lastPort: Int = 8080

    init {
        viewModelScope.launch {
            repository.incomingMessages.collect { message ->
                _messages.update { it + message }
            }
        }

        // Observe underlying connection state and trigger auto-reconnect on unexpected loss
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                when {
                    state is ConnectionState.Error && state.type == ErrorType.CONNECTION_LOST && !userDisconnected -> {
                        startReconnect()
                    }
                    else -> {
                        _uiConnectionState.value = state
                    }
                }
            }
        }
    }

    fun onMessageInputChanged(text: String) {
        _messageInput.value = text
    }

    fun onConnectClicked(host: String, port: Int, username: String = "karsu") {
        reconnectJob?.cancel()
        userDisconnected = false
        currentUsername = username
        lastHost = host
        lastPort = port
        // Clear chat history on new connection
        _messages.value = emptyList()
        viewModelScope.launch {
            repository.connect(host, port, username)
        }
    }

    fun onDisconnectClicked() {
        userDisconnected = true
        reconnectJob?.cancel()
        // Clear chat history on disconnect
        _messages.value = emptyList()
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    /**
     * Attempts to reconnect with exponential backoff.
     * Preserves messages during reconnection attempts.
     */
    private fun startReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                _uiConnectionState.value = ConnectionState.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)

                val backoff = min(INITIAL_BACKOFF_MS * (1L shl (attempt - 1)), MAX_BACKOFF_MS)
                delay(backoff)

                if (!isActive || userDisconnected) return@launch

                repository.connect(lastHost, lastPort, currentUsername)

                // Wait briefly for connection to establish
                delay(2000)

                if (repository.connectionState.value is ConnectionState.Connected) return@launch
            }

            // All attempts exhausted
            _uiConnectionState.value = ConnectionState.Error(
                ErrorType.CONNECTION_FAILED, "Reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts"
            )
        }
    }

    fun onSendClicked() {
        val text = _messageInput.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val myMessage = ChatMessage(
                sender = currentUsername,
                content = text,
                isFromMe = true
            )
            _messages.update { it + myMessage }
            repository.sendMessage(text, currentUsername)
            _messageInput.value = ""
        }
    }

    /** Starts voice recording. Updates [isRecording] state on success. */
    fun onRecordStart() {
        val started = audioRecorder.startRecording()
        _isRecording.value = started
    }

    /**
     * Stops voice recording, encodes the audio as Base64, adds it to the message list
     * optimistically, and sends it over WebSocket. Deletes the temp file afterwards.
     */
    fun onRecordEnd() {
        _isRecording.value = false
        val result = audioRecorder.stopRecording() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioBytes = result.file.readBytes()
                val base64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                val voiceMessage = ChatMessage(
                    sender = currentUsername,
                    content = "",
                    type = "voice",
                    audioData = base64,
                    audioDuration = result.durationMs,
                    isFromMe = true
                )

                _messages.update { it + voiceMessage }
                repository.sendVoiceMessage(base64, result.durationMs, currentUsername)
            } finally {
                result.file.delete()
            }
        }
    }

    /** Toggles play/stop for a voice message. */
    fun onPlayVoice(message: ChatMessage) {
        val data = message.audioData ?: return
        if (audioPlayer.isPlaying) {
            audioPlayer.stop()
        } else {
            audioPlayer.play(data)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.close()
        audioPlayer.close()
        // viewModelScope is already cancelled in onCleared, use GlobalScope for cleanup
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            repository.disconnect()
        }
    }
}
