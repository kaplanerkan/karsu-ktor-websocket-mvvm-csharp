package com.panda.ktorwebsocketmvvm.ui.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panda.ktorwebsocketmvvm.data.audio.AudioPlayer
import com.panda.ktorwebsocketmvvm.data.audio.AudioRecorder
import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ViewModel for the chat screen. Manages connection lifecycle, text and voice messaging,
 * and audio recording/playback state.
 *
 * Injected via Koin with [ChatRepository], [AudioRecorder], and [AudioPlayer].
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile
    private var currentUsername: String = "karsu"

    init {
        viewModelScope.launch {
            repository.incomingMessages.collect { message ->
                _messages.update { it + message }
            }
        }
    }

    fun onMessageInputChanged(text: String) {
        _messageInput.value = text
    }

    fun onConnectClicked(host: String, port: Int, username: String = "karsu") {
        currentUsername = username
        // Clear chat history on new connection
        _messages.value = emptyList()
        viewModelScope.launch {
            repository.connect(host, port, username)
        }
    }

    fun onDisconnectClicked() {
        // Clear chat history on disconnect
        _messages.value = emptyList()
        viewModelScope.launch {
            repository.disconnect()
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
