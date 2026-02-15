package com.karsu.plugins

import com.karsu.model.ChatMessage
import com.karsu.session.ConnectionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform

fun Application.configureRouting() {
    val connectionManager = KoinPlatform.getKoin().get<ConnectionManager>()

    routing {

        // Web chat client
        get("/") {
            val html = this::class.java.classLoader.getResource("web/chat.html")?.readText()
                ?: "Chat page not found"
            call.respondText(html, ContentType.Text.Html)
        }

        // Health check endpoint
        get("/health") {
            call.respondText("Server is running", status = HttpStatusCode.OK)
        }

        // Connected client list (JSON array)
        get("/clients") {
            val clients = connectionManager.getConnectedClients()
            call.respondText(
                Json.encodeToString(clients.toList()),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        }

        // Room members
        get("/rooms/{roomId}/clients") {
            val roomId = call.parameters["roomId"] ?: "general"
            val members = connectionManager.getRoomMembers(roomId)
            call.respondText(
                Json.encodeToString(members.toList()),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        }

        // Active rooms
        get("/rooms") {
            val rooms = connectionManager.getActiveRooms()
            call.respondText(
                Json.encodeToString(rooms.toList()),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        }

        /**
         * REST endpoint to send a message via HTTP POST.
         * Broadcasts to all connected WebSocket clients.
         */
        post("/send") {
            val message = call.receive<ChatMessage>()
            val jsonString = Json.encodeToString(message)
            println("[REST /send] ${message.sender}: ${message.content}")
            connectionManager.broadcast(jsonString)
            call.respondText("Message sent to ${connectionManager.getConnectedClients().size} client(s)", status = HttpStatusCode.OK)
        }

        /**
         * WebSocket endpoint (default room: "general")
         * Connection: ws://HOST:8080/chat/{clientId}
         */
        webSocket("/chat/{clientId}") {
            val clientId = call.parameters["clientId"] ?: "unknown"
            handleWebSocket(connectionManager, clientId, "general")
        }

        /**
         * WebSocket endpoint with room support
         * Connection: ws://HOST:8080/chat/{roomId}/{clientId}
         */
        webSocket("/chat/{roomId}/{clientId}") {
            val roomId = call.parameters["roomId"] ?: "general"
            val clientId = call.parameters["clientId"] ?: "unknown"
            handleWebSocket(connectionManager, clientId, roomId)
        }
    }
}

/**
 * Shared WebSocket handler for both room-based and default connections.
 */
private suspend fun DefaultWebSocketServerSession.handleWebSocket(
    connectionManager: ConnectionManager,
    clientId: String,
    roomId: String
) {
    connectionManager.addConnection(clientId, this, roomId)

    // Welcome message
    val welcomeMsg = ChatMessage(
        sender = "server",
        content = "Welcome $clientId! Room: $roomId"
    )
    send(Frame.Text(Json.encodeToString(welcomeMsg)))

    try {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    val logText = if (text.length > 200) text.take(200) + "...[truncated]" else text
                    println("[$roomId/$clientId]: $logText")

                    try {
                        val msg = Json.decodeFromString<ChatMessage>(text)

                        // Skip status and typing messages from delivery tracking
                        val isTrackable = msg.type != "typing" && msg.type != "status" && !msg.messageId.isNullOrBlank()

                        when {
                            msg.type == "status" -> {
                                // Forward read/delivery receipts to the original sender
                                if (!msg.sendTo.isNullOrBlank()) {
                                    connectionManager.sendTo(msg.sendTo, text)
                                }
                            }
                            !msg.sendTo.isNullOrBlank() -> {
                                connectionManager.sendTo(msg.sendTo, text)
                            }
                            else -> {
                                connectionManager.broadcastToRoom(roomId, text, excludeClientId = clientId)
                            }
                        }

                        // Send "delivered" ack back to the sender
                        if (isTrackable && msg.type != "status") {
                            val ack = ChatMessage(
                                sender = "server",
                                content = "",
                                type = "status",
                                messageId = msg.messageId,
                                status = "delivered"
                            )
                            connectionManager.sendTo(clientId, Json.encodeToString(ack))
                        }
                    } catch (_: Exception) {
                        connectionManager.broadcastToRoom(roomId, text, excludeClientId = clientId)
                    }
                }
                is Frame.Close -> {
                    println("[$roomId/$clientId] closed connection")
                }
                else -> { /* Ping/Pong handled automatically */ }
            }
        }
    } catch (e: Exception) {
        println("[$roomId/$clientId] error: ${e.message}")
    } finally {
        connectionManager.removeConnection(clientId)
    }
}
