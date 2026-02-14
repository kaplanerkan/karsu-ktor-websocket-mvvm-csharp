package com.panda.ktorwebsocketmvvm.data.model

import org.junit.Assert.*
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun `Disconnected is singleton`() {
        assertSame(ConnectionState.Disconnected, ConnectionState.Disconnected)
    }

    @Test
    fun `Connecting is singleton`() {
        assertSame(ConnectionState.Connecting, ConnectionState.Connecting)
    }

    @Test
    fun `Connected is singleton`() {
        assertSame(ConnectionState.Connected, ConnectionState.Connected)
    }

    @Test
    fun `Error holds type and detail`() {
        val error = ConnectionState.Error(ErrorType.CONNECTION_FAILED, "timeout")
        assertEquals(ErrorType.CONNECTION_FAILED, error.type)
        assertEquals("timeout", error.detail)
    }

    @Test
    fun `Error detail defaults to null`() {
        val error = ConnectionState.Error(ErrorType.CONNECTION_LOST)
        assertNull(error.detail)
    }

    @Test
    fun `Error equality checks type and detail`() {
        val e1 = ConnectionState.Error(ErrorType.MESSAGE_FAILED, "fail")
        val e2 = ConnectionState.Error(ErrorType.MESSAGE_FAILED, "fail")
        assertEquals(e1, e2)
    }

    @Test
    fun `different Error types are not equal`() {
        val e1 = ConnectionState.Error(ErrorType.CONNECTION_LOST, "x")
        val e2 = ConnectionState.Error(ErrorType.CONNECTION_FAILED, "x")
        assertNotEquals(e1, e2)
    }

    @Test
    fun `all states are ConnectionState subtypes`() {
        val states: List<ConnectionState> = listOf(
            ConnectionState.Disconnected,
            ConnectionState.Connecting,
            ConnectionState.Connected,
            ConnectionState.Error(ErrorType.CONNECTION_LOST)
        )
        assertEquals(4, states.size)
        states.forEach { assertTrue(it is ConnectionState) }
    }

    @Test
    fun `ErrorType enum has all expected values`() {
        val types = ErrorType.entries
        assertEquals(3, types.size)
        assertTrue(types.contains(ErrorType.CONNECTION_LOST))
        assertTrue(types.contains(ErrorType.CONNECTION_FAILED))
        assertTrue(types.contains(ErrorType.MESSAGE_FAILED))
    }
}
