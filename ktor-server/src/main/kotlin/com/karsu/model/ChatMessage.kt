package com.karsu.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val sender: String,       // "android", "csharp", "server"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
