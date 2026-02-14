package com.panda.ktorwebsocketmvvm.data.remote

import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.model.ErrorType
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WebSocket bağlantısını yöneten data source.
 * MVVM'de Repository katmanı bunu kullanır.
 */
class WebSocketDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(replay = 0)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    /**
     * WebSocket sunucusuna bağlanır ve gelen mesajları dinler.
     */
    suspend fun connect(host: String, port: Int, clientId: String = "android-1") {
        if (_connectionState.value == ConnectionState.Connected) return

        _connectionState.value = ConnectionState.Connecting

        try {
            connectionJob = CoroutineScope(Dispatchers.IO).launch {
                client.webSocket(
                    host = host,
                    port = port,
                    path = "/chat/$clientId"
                ) {
                    session = this
                    _connectionState.value = ConnectionState.Connected

                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    try {
                                        val message = json.decodeFromString<ChatMessage>(text)
                                        _incomingMessages.emit(message)
                                    } catch (e: Exception) {
                                        val fallback = ChatMessage(
                                            sender = "unknown",
                                            content = text
                                        )
                                        _incomingMessages.emit(fallback)
                                    }
                                }
                                is Frame.Close -> {
                                    _connectionState.value = ConnectionState.Disconnected
                                }
                                else -> { /* Ping/Pong */ }
                            }
                        }
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.Error(
                            ErrorType.CONNECTION_LOST, e.message
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(
                ErrorType.CONNECTION_FAILED, e.message
            )
        }
    }

    /**
     * Sunucuya mesaj gönderir.
     */
    suspend fun sendMessage(message: ChatMessage) {
        try {
            val jsonString = json.encodeToString(message)
            session?.send(Frame.Text(jsonString))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(
                ErrorType.MESSAGE_FAILED, e.message
            )
        }
    }

    /**
     * Bağlantıyı kapatır.
     */
    suspend fun disconnect() {
        try {
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed"))
        } catch (_: Exception) { }
        connectionJob?.cancel()
        session = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
