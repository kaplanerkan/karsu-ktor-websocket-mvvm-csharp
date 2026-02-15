package com.karsu.session

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all connected WebSocket clients with room support.
 * Provides broadcast, room-based broadcast, and targeted message sending.
 */
class ConnectionManager {

    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    // Room membership: roomId -> set of clientIds
    private val rooms = ConcurrentHashMap<String, MutableSet<String>>()
    // Reverse map: clientId -> roomId
    private val clientRooms = ConcurrentHashMap<String, String>()

    fun addConnection(clientId: String, session: WebSocketSession, roomId: String = "general") {
        connections[clientId] = session
        rooms.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(clientId)
        clientRooms[clientId] = roomId
        println("Client connected: $clientId in room '$roomId' (Total: ${connections.size})")
    }

    fun removeConnection(clientId: String) {
        val roomId = clientRooms.remove(clientId)
        if (roomId != null) {
            rooms[roomId]?.remove(clientId)
            if (rooms[roomId]?.isEmpty() == true) rooms.remove(roomId)
        }
        connections.remove(clientId)
        println("Client disconnected: $clientId (Total: ${connections.size})")
    }

    /**
     * Broadcasts message to everyone except the sender (global)
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
     * Broadcasts message to everyone in the same room
     */
    suspend fun broadcastToRoom(roomId: String, message: String, excludeClientId: String? = null) {
        val members = rooms[roomId] ?: return
        members.forEach { clientId ->
            if (clientId != excludeClientId) {
                connections[clientId]?.let { session ->
                    try {
                        session.send(Frame.Text(message))
                    } catch (e: Exception) {
                        println("Message send failed: $clientId - ${e.message}")
                        removeConnection(clientId)
                    }
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

    fun getRoomMembers(roomId: String): Set<String> = rooms[roomId]?.toSet() ?: emptySet()

    fun getActiveRooms(): Set<String> = rooms.keys.toSet()

    fun getClientRoom(clientId: String): String? = clientRooms[clientId]
}
