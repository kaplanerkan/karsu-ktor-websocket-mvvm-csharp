package com.karsu.session

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all connected WebSocket clients.
 * Provides broadcast and targeted message sending.
 */
class ConnectionManager {

    private val connections = ConcurrentHashMap<String, WebSocketSession>()

    fun addConnection(clientId: String, session: WebSocketSession) {
        connections[clientId] = session
        println("Client connected: $clientId (Total: ${connections.size})")
    }

    fun removeConnection(clientId: String) {
        connections.remove(clientId)
        println("Client disconnected: $clientId (Total: ${connections.size})")
    }

    /**
     * Broadcasts message to everyone except the sender
     */
    suspend fun broadcast(message: String, excludeClientId: String? = null) {
        connections.forEach { (clientId, session) ->
            if (clientId != excludeClientId) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    println("Message send failed: $clientId - ${e.message}")
                    removeConnection(clientId)
                }
            }
        }
    }

    /**
     * Sends message to a specific client
     */
    suspend fun sendTo(clientId: String, message: String) {
        connections[clientId]?.let { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Message send failed: $clientId - ${e.message}")
                removeConnection(clientId)
            }
        }
    }

    fun getConnectedClients(): Set<String> = connections.keys.toSet()
}
