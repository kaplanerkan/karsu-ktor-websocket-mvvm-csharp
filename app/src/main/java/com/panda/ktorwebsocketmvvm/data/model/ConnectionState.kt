package com.panda.ktorwebsocketmvvm.data.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    data class Error(val type: ErrorType, val detail: String? = null) : ConnectionState()
}

enum class ErrorType {
    CONNECTION_LOST,
    CONNECTION_FAILED,
    MESSAGE_FAILED
}
