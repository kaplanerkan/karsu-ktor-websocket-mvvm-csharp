package com.panda.ktorwebsocketmvvm.data.repository

import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.remote.WebSocketDataSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository that abstracts the [WebSocketDataSource] for the ViewModel layer.
 * Exposes connection state, incoming messages, and methods to send text or voice messages.
 */
class ChatRepository(
    private val webSocketDataSource: WebSocketDataSource
) {

    val connectionState: StateFlow<ConnectionState>
        get() = webSocketDataSource.connectionState

    val incomingMessages: SharedFlow<ChatMessage>
        get() = webSocketDataSource.incomingMessages

    suspend fun connect(host: String, port: Int, clientId: String) {
        webSocketDataSource.connect(host, port, clientId)
    }

    suspend fun sendMessage(content: String, sender: String) {
        val message = ChatMessage(
            sender = sender,
            content = content
        )
        webSocketDataSource.sendMessage(message)
    }

    /**
     * Sends a voice message with Base64-encoded audio data over WebSocket.
     * @param audioData Base64-encoded AAC/M4A audio bytes.
     * @param durationMs Recording duration in milliseconds.
     * @param sender    Display name of the sender.
     */
    suspend fun sendVoiceMessage(audioData: String, durationMs: Long, sender: String) {
        val message = ChatMessage(
            sender = sender,
            content = "",
            type = "voice",
            audioData = audioData,
            audioDuration = durationMs
        )
        webSocketDataSource.sendMessage(message)
    }

    suspend fun disconnect() {
        webSocketDataSource.disconnect()
    }
}
