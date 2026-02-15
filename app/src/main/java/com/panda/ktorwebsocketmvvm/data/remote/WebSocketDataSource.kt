package com.panda.ktorwebsocketmvvm.data.remote

import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.model.ErrorType
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable

/**
 * WebSocket bağlantısını yöneten data source.
 * MVVM'de Repository katmanı bunu kullanır.
 */
class WebSocketDataSource : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }

    private val mutex = Mutex()
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var connectionJob: Job? = null

    @Volatile private var connectedHost: String = ""
    @Volatile private var connectedPort: Int = 8080

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(replay = 0)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    /**
     * WebSocket sunucusuna bağlanır ve gelen mesajları dinler.
     */
    suspend fun connect(host: String, port: Int, clientId: String = "android-1", roomId: String = "general") {
        mutex.withLock {
            if (_connectionState.value == ConnectionState.Connected ||
                _connectionState.value == ConnectionState.Connecting) return

            // Cancel previous connection if exists
            connectionJob?.cancel()
            session = null

            _connectionState.value = ConnectionState.Connecting
        }

        connectedHost = host
        connectedPort = port

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val path = if (roomId == "general") "/chat/$clientId" else "/chat/$roomId/$clientId"
                client.webSocket(
                    host = host,
                    port = port,
                    path = path
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
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(
                    ErrorType.CONNECTION_FAILED, e.message
                )
            } finally {
                session = null
            }
        }
    }

    /**
     * Sunucuya mesaj gönderir.
     */
    suspend fun sendMessage(message: ChatMessage) {
        try {
            val jsonString = json.encodeToString(message)
            val currentSession = session
            if (currentSession == null) {
                _connectionState.value = ConnectionState.Error(
                    ErrorType.MESSAGE_FAILED, "Not connected"
                )
                return
            }
            currentSession.send(Frame.Text(jsonString))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(
                ErrorType.MESSAGE_FAILED, e.message
            )
        }
    }

    /**
     * Fetches the list of currently connected clients from the REST endpoint.
     */
    suspend fun fetchOnlineUsers(): List<String> {
        return try {
            val response: HttpResponse = client.get("http://$connectedHost:$connectedPort/clients")
            val text = response.bodyAsText()
            json.decodeFromString<List<String>>(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Bağlantıyı kapatır.
     */
    suspend fun disconnect() {
        try {
            withTimeout(5000L) {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed"))
            }
        } catch (_: Exception) { }
        connectionJob?.cancel()
        session = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun close() {
        runBlocking {
            disconnect()
        }
        client.close()
    }
}
