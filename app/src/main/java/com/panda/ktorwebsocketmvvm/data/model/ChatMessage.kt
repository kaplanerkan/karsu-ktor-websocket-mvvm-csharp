package com.panda.ktorwebsocketmvvm.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChatMessage(
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    @Transient val isFromMe: Boolean = false
) {
    val isFromServer: Boolean get() = sender == "server"
}
