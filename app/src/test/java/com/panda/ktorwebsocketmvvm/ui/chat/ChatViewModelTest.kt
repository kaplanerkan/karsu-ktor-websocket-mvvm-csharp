package com.panda.ktorwebsocketmvvm.ui.chat

import com.panda.ktorwebsocketmvvm.data.model.ChatMessage
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.remote.WebSocketDataSource
import com.panda.ktorwebsocketmvvm.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataSource: WebSocketDataSource
    private lateinit var repository: ChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dataSource = WebSocketDataSource()
        repository = ChatRepository(dataSource)
        viewModel = ChatViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is disconnected`() {
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.value)
    }

    @Test
    fun `initial messages list is empty`() {
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `initial message input is empty`() {
        assertEquals("", viewModel.messageInput.value)
    }

    @Test
    fun `onMessageInputChanged updates messageInput`() {
        viewModel.onMessageInputChanged("Hello")
        assertEquals("Hello", viewModel.messageInput.value)
    }

    @Test
    fun `onSendClicked does nothing if input is empty`() {
        viewModel.onMessageInputChanged("")
        viewModel.onSendClicked()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `onSendClicked does nothing if input is only whitespace`() {
        viewModel.onMessageInputChanged("   ")
        viewModel.onSendClicked()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `onSendClicked adds message to list`() = runTest {
        viewModel.onMessageInputChanged("Test message")
        viewModel.onSendClicked()

        val messages = viewModel.messages.value
        assertEquals(1, messages.size)
        assertEquals("Test message", messages[0].content)
        assertTrue(messages[0].isFromMe)
    }

    @Test
    fun `onSendClicked uses currentUsername as sender`() = runTest {
        viewModel.onConnectClicked("localhost", 8080, "myuser")
        viewModel.onMessageInputChanged("Hi")
        viewModel.onSendClicked()

        val messages = viewModel.messages.value
        assertEquals("myuser", messages[0].sender)
    }

    @Test
    fun `onSendClicked clears input after send`() = runTest {
        viewModel.onMessageInputChanged("Hello")
        viewModel.onSendClicked()

        assertEquals("", viewModel.messageInput.value)
    }

    @Test
    fun `onConnectClicked clears messages`() {
        // Simulate existing messages via send
        viewModel.onMessageInputChanged("old msg")
        viewModel.onSendClicked()
        assertFalse(viewModel.messages.value.isEmpty())

        viewModel.onConnectClicked("localhost", 8080, "user")
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `onDisconnectClicked clears messages`() {
        viewModel.onMessageInputChanged("old msg")
        viewModel.onSendClicked()
        assertFalse(viewModel.messages.value.isEmpty())

        viewModel.onDisconnectClicked()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `default username is karsu`() = runTest {
        viewModel.onMessageInputChanged("test")
        viewModel.onSendClicked()

        assertEquals("karsu", viewModel.messages.value[0].sender)
    }

    @Test
    fun `multiple messages accumulate in list`() = runTest {
        viewModel.onMessageInputChanged("msg1")
        viewModel.onSendClicked()
        viewModel.onMessageInputChanged("msg2")
        viewModel.onSendClicked()
        viewModel.onMessageInputChanged("msg3")
        viewModel.onSendClicked()

        assertEquals(3, viewModel.messages.value.size)
        assertEquals("msg1", viewModel.messages.value[0].content)
        assertEquals("msg2", viewModel.messages.value[1].content)
        assertEquals("msg3", viewModel.messages.value[2].content)
    }
}
