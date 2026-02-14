package com.karsu.plugins

import com.karsu.model.ChatMessage
import com.karsu.session.ConnectionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val connectionManager by inject<ConnectionManager>()

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
                            println("[$clientId]: $text")

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
