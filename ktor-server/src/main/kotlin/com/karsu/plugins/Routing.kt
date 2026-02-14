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

        // Health check endpoint
        get("/health") {
            call.respondText("Server is running", status = HttpStatusCode.OK)
        }

        // Connected client list
        get("/clients") {
            val clients = connectionManager.getConnectedClients()
            call.respondText(clients.toString(), status = HttpStatusCode.OK)
        }

        /**
         * REST endpoint to send a message via HTTP POST.
         * Broadcasts to all connected WebSocket clients.
         *
         * curl -X POST http://HOST:8080/send \
         *   -H "Content-Type: application/json" \
         *   -d '{"sender":"curl","content":"Hello from curl!"}'
         */
        post("/send") {
            val message = call.receive<ChatMessage>()
            val jsonString = Json.encodeToString(message)
            println("[REST /send] ${message.sender}: ${message.content}")
            connectionManager.broadcast(jsonString)
            call.respondText("Message sent to ${connectionManager.getConnectedClients().size} client(s)", status = HttpStatusCode.OK)
        }

        /**
         * WebSocket endpoint
         * Connection: ws://HOST:8080/chat/{clientId}
         *
         * clientId examples: "android-1", "csharp-1"
         */
        webSocket("/chat/{clientId}") {
            val clientId = call.parameters["clientId"] ?: "unknown"

            connectionManager.addConnection(clientId, this)

            // Welcome message
            val welcomeMsg = ChatMessage(
                sender = "server",
                content = "Welcome $clientId!"
            )
            send(Frame.Text(Json.encodeToString(welcomeMsg)))

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            val logText = if (text.length > 200) text.take(200) + "...[truncated]" else text
                            println("[$clientId]: $logText")

                            // Broadcast message to all other clients
                            connectionManager.broadcast(text, excludeClientId = clientId)
                        }
                        is Frame.Close -> {
                            println("[$clientId] closed connection")
                        }
                        else -> { /* Ping/Pong handled automatically */ }
                    }
                }
            } catch (e: Exception) {
                println("[$clientId] error: ${e.message}")
            } finally {
                connectionManager.removeConnection(clientId)
            }
        }
    }
}
