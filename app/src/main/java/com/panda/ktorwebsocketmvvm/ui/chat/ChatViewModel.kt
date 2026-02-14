package com.panda.ktorwebsocketmvvm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.disconnect()
        }
    }
}
