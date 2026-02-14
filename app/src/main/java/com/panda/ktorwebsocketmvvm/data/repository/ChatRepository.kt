package com.panda.ktorwebsocketmvvm.data.repository

import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.remote.WebSocketDataSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

    suspend fun disconnect() {
        webSocketDataSource.disconnect()
    }
}
