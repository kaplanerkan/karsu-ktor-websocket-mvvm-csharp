package com.panda.ktorwebsocketmvvm.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatMessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default timestamp is set automatically`() {
        val before = System.currentTimeMillis()
        val message = ChatMessage(sender = "test", content = "hello")
        val after = System.currentTimeMillis()

        assertTrue(message.timestamp in before..after)
    }

    @Test
    fun `isFromMe defaults to false`() {
        val message = ChatMessage(sender = "user", content = "hi")
        assertFalse(message.isFromMe)
    }

    @Test
    fun `isFromMe can be set to true`() {
        val message = ChatMessage(sender = "user", content = "hi", isFromMe = true)
        assertTrue(message.isFromMe)
    }

    @Test
    fun `isFromServer returns true for server sender`() {
        val message = ChatMessage(sender = "server", content = "Welcome!")
        assertTrue(message.isFromServer)
    }

    @Test
    fun `isFromServer returns false for non-server sender`() {
        val message = ChatMessage(sender = "karsu", content = "hello")
        assertFalse(message.isFromServer)
    }

    @Test
    fun `serialization produces correct JSON fields`() {
        val message = ChatMessage(sender = "karsu", content = "test", timestamp = 1000L)
        val jsonStr = json.encodeToString(message)

        assertTrue(jsonStr.contains("\"sender\":\"karsu\""))
        assertTrue(jsonStr.contains("\"content\":\"test\""))
        assertTrue(jsonStr.contains("\"timestamp\":1000"))
    }

    @Test
    fun `isFromMe is excluded from serialization`() {
        val message = ChatMessage(sender = "karsu", content = "test", isFromMe = true)
        val jsonStr = json.encodeToString(message)

        assertFalse(jsonStr.contains("isFromMe"))
    }

    @Test
    fun `deserialization works with valid JSON`() {
        val jsonStr = """{"sender":"papa-1","content":"hello","timestamp":12345}"""
        val message = json.decodeFromString<ChatMessage>(jsonStr)

        assertEquals("papa-1", message.sender)
        assertEquals("hello", message.content)
        assertEquals(12345L, message.timestamp)
        assertFalse(message.isFromMe)
    }

    @Test
    fun `deserialization ignores unknown fields`() {
        val jsonStr = """{"sender":"x","content":"y","timestamp":1,"extra":"ignored"}"""
        val message = json.decodeFromString<ChatMessage>(jsonStr)

        assertEquals("x", message.sender)
        assertEquals("y", message.content)
    }

    @Test
    fun `data class equality works`() {
        val m1 = ChatMessage(sender = "a", content = "b", timestamp = 100)
        val m2 = ChatMessage(sender = "a", content = "b", timestamp = 100)
        assertEquals(m1, m2)
    }

    @Test
    fun `data class equality includes isFromMe`() {
        val m1 = ChatMessage(sender = "a", content = "b", timestamp = 100, isFromMe = true)
        val m2 = ChatMessage(sender = "a", content = "b", timestamp = 100, isFromMe = false)
        // @Transient only excludes from serialization, not from equals/hashCode
        assertNotEquals(m1, m2)
    }
}
