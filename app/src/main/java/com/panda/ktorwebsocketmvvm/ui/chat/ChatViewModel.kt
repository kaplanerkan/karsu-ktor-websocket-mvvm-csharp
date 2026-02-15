package com.panda.ktorwebsocketmvvm.ui.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panda.ktorwebsocketmvvm.data.audio.AudioPlayer
import com.panda.ktorwebsocketmvvm.data.audio.AudioRecorder
import com.panda.ktorwebsocketmvvm.data.audio.SoundEffectManager
import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.model.ErrorType
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import com.panda.ktorwebsocketmvvm.notification.ChatNotificationManager
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
    private val audioPlayer: AudioPlayer,
    private val soundEffects: SoundEffectManager,
    private val notificationManager: ChatNotificationManager
) : ViewModel() {

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val TYPING_DEBOUNCE_MS = 2000L
        private const val TYPING_EXPIRE_MS = 4000L
        private const val ONLINE_USERS_POLL_MS = 5000L
    }

    private val _uiConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _uiConnectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Typing indicator state
    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingText: StateFlow<String> = _typingUsers.map { users ->
        when {
            users.isEmpty() -> ""
            users.size == 1 -> "${users.first()} is typing..."
            users.size == 2 -> "${users.joinToString(" and ")} are typing..."
            else -> "${users.take(2).joinToString(", ")} and ${users.size - 2} more are typing..."
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _onlineUsers = MutableStateFlow<List<String>>(emptyList())
    val onlineUsers: StateFlow<List<String>> = _onlineUsers.asStateFlow()

    private val _dmTarget = MutableStateFlow<String?>(null)
    val dmTarget: StateFlow<String?> = _dmTarget.asStateFlow()

    private var onlineUsersJob: Job? = null
    private var typingJob: Job? = null
    private var lastTypingSentTime = 0L
    private val typingExpireJobs = mutableMapOf<String, Job>()

    @Volatile
    private var currentUsername: String = "karsu"

    private var userDisconnected = false
    private var reconnectJob: Job? = null
    private var lastHost: String = ""
    private var lastPort: Int = 8080
    private var lastRoomId: String = "general"

    init {
        viewModelScope.launch {
            repository.incomingMessages.collect { message ->
                if (message.isTyping) {
                    handleIncomingTypingIndicator(message)
                } else if (message.isStatusUpdate) {
                    // Update status of an existing message
                    val msgId = message.messageId ?: return@collect
                    _messages.update { list ->
                        list.map { msg ->
                            if (msg.messageId == msgId) msg.copy(status = message.status)
                            else msg
                        }
                    }
                } else {
                    // A real message from this sender means they stopped typing
                    _typingUsers.update { it - message.sender }
                    typingExpireJobs.remove(message.sender)?.cancel()
                    val displayMsg = if (message.isDirectMessage) {
                        message.copy(content = "[DM] ${message.content}")
                    } else message
                    _messages.update { it + displayMsg }
                    soundEffects.playReceiveSound()
                    notificationManager.showIfNeeded(displayMsg)
                }
            }
        }

        // Observe underlying connection state and trigger auto-reconnect on unexpected loss
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                when {
                    state is ConnectionState.Error && state.type == ErrorType.CONNECTION_LOST && !userDisconnected -> {
                        stopOnlineUsersPolling()
                        startReconnect()
                    }
                    state is ConnectionState.Connected -> {
                        _uiConnectionState.value = state
                        startOnlineUsersPolling()
                    }
                    else -> {
                        _uiConnectionState.value = state
                        if (state is ConnectionState.Disconnected) stopOnlineUsersPolling()
                    }
                }
            }
        }
    }

    fun onMessageInputChanged(text: String) {
        _messageInput.value = text

        if (text.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastTypingSentTime >= TYPING_DEBOUNCE_MS) {
                lastTypingSentTime = now
                viewModelScope.launch {
                    repository.sendTypingIndicator(isTyping = true, sender = currentUsername)
                }
            }
            // Schedule a "stop" if user pauses typing
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(TYPING_DEBOUNCE_MS)
                repository.sendTypingIndicator(isTyping = false, sender = currentUsername)
                lastTypingSentTime = 0L
            }
        } else {
            sendTypingStop()
        }
    }

    fun onConnectClicked(host: String, port: Int, username: String = "karsu", roomId: String = "general") {
        reconnectJob?.cancel()
        userDisconnected = false
        currentUsername = username
        lastHost = host
        lastPort = port
        lastRoomId = roomId
        _messages.value = emptyList()
        clearTypingState()
        viewModelScope.launch {
            repository.connect(host, port, username, roomId)
        }
    }

    fun onDisconnectClicked() {
        sendTypingStop()
        clearTypingState()
        stopOnlineUsersPolling()
        userDisconnected = true
        reconnectJob?.cancel()
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

                repository.connect(lastHost, lastPort, currentUsername, lastRoomId)

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

        val target = _dmTarget.value
        sendTypingStop()
        viewModelScope.launch {
            val msgId = repository.sendMessage(text, currentUsername, sendTo = target)
            val myMessage = ChatMessage(
                sender = currentUsername,
                content = if (target != null) "[DM to $target] $text" else text,
                sendTo = target,
                messageId = msgId,
                status = "sent",
                isFromMe = true
            )
            _messages.update { it + myMessage }
            soundEffects.playSendSound()
            _messageInput.value = ""
        }
    }

    fun setDmTarget(target: String?) {
        _dmTarget.value = target
    }

    // ── Online Users Polling ─────────────────────────────

    private fun startOnlineUsersPolling() {
        onlineUsersJob?.cancel()
        onlineUsersJob = viewModelScope.launch {
            while (isActive) {
                val users = repository.fetchOnlineUsers()
                _onlineUsers.value = users
                delay(ONLINE_USERS_POLL_MS)
            }
        }
    }

    private fun stopOnlineUsersPolling() {
        onlineUsersJob?.cancel()
        _onlineUsers.value = emptyList()
    }

    // ── Typing Indicator Helpers ─────────────────────────

    private fun handleIncomingTypingIndicator(message: ChatMessage) {
        val sender = message.sender
        if (message.content == "start") {
            _typingUsers.update { it + sender }
            typingExpireJobs[sender]?.cancel()
            typingExpireJobs[sender] = viewModelScope.launch {
                delay(TYPING_EXPIRE_MS)
                _typingUsers.update { it - sender }
                typingExpireJobs.remove(sender)
            }
        } else {
            _typingUsers.update { it - sender }
            typingExpireJobs.remove(sender)?.cancel()
        }
    }

    private fun sendTypingStop() {
        typingJob?.cancel()
        typingJob = null
        lastTypingSentTime = 0L
        viewModelScope.launch {
            repository.sendTypingIndicator(isTyping = false, sender = currentUsername)
        }
    }

    private fun clearTypingState() {
        _typingUsers.value = emptySet()
        typingExpireJobs.values.forEach { it.cancel() }
        typingExpireJobs.clear()
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
                soundEffects.playSendSound()
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
        soundEffects.release()
        // viewModelScope is already cancelled in onCleared, use GlobalScope for cleanup
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            repository.disconnect()
        }
    }
}
