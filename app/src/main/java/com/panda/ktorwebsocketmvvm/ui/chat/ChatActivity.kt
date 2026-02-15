package com.panda.ktorwebsocketmvvm.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.panda.ktorwebsocketmvvm.ChatApplication
import com.panda.ktorwebsocketmvvm.R
import com.panda.ktorwebsocketmvvm.data.model.ConnectionState
import com.panda.ktorwebsocketmvvm.data.model.ErrorType
import com.panda.ktorwebsocketmvvm.databinding.ActivityChatBinding
import com.panda.ktorwebsocketmvvm.databinding.DialogSettingsBinding
import com.panda.ktorwebsocketmvvm.notification.ChatNotificationManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Main chat screen activity.
 * Handles connection UI, text/voice messaging, and settings.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModel()
    private val notificationManager: ChatNotificationManager by inject()
    private lateinit var adapter: ChatAdapter

    /** Permission launcher for RECORD_AUDIO — starts recording on grant. */
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onRecordStart()
        } else {
            Toast.makeText(this, R.string.error_mic_permission, Toast.LENGTH_SHORT).show()
        }
    }

    /** Permission launcher for POST_NOTIFICATIONS (Android 13+). */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — no action needed */ }

    private val prefs by lazy {
        getSharedPreferences("connection_settings", Context.MODE_PRIVATE)
    }

    private var savedHost: String
        get() = prefs.getString("host", "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString("host", value).apply()

    private var savedPort: Int
        get() = prefs.getInt("port", 8080)
        set(value) = prefs.edit().putInt("port", value).apply()

    private var savedUsername: String
        get() = prefs.getString("username", "karsu") ?: "karsu"
        set(value) = prefs.edit().putString("username", value).apply()

    private var savedRoom: String
        get() = prefs.getString("room", "general") ?: "general"
        set(value) = prefs.edit().putString("room", value).apply()

    private var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        notificationManager.isAppInForeground = true
        notificationManager.dismiss()
    }

    override fun onPause() {
        super.onPause()
        notificationManager.isAppInForeground = false
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter { message -> viewModel.onPlayVoice(message) }
        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@ChatActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnConnect.setOnClickListener {
            viewModel.onConnectClicked(savedHost, savedPort, savedUsername, savedRoom)
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.onDisconnectClicked()
        }

        binding.btnSend.setOnClickListener {
            viewModel.onSendClicked()
            binding.etMessage.text.clear()
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onMessageInputChanged(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        setupMicButton()

        binding.tvOnlineUsers.setOnClickListener {
            val users = viewModel.onlineUsers.value
            if (users.isNotEmpty()) {
                val items = arrayOf("Broadcast (All)") + users.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.online_users_title)
                    .setItems(items) { _, which ->
                        if (which == 0) {
                            viewModel.setDmTarget(null)
                        } else {
                            viewModel.setDmTarget(users[which - 1])
                        }
                    }
                    .show()
            }
        }

        binding.btnEmoji.setOnClickListener {
            showEmojiPicker()
        }
    }

    /**
     * Sets up the mic button with hold-to-record touch handling.
     * Requests RECORD_AUDIO permission on first press if not already granted.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMicButton() {
        binding.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasAudioPermission()) {
                        viewModel.onRecordStart()
                    } else {
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (viewModel.isRecording.value) {
                        viewModel.onRecordEnd()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))

        // Server info with device IP
        val deviceIp = getDeviceIpAddress()
        val serverPort = ChatApplication.SERVER_PORT
        dialogBinding.tvServerInfo.text = if (deviceIp != null) {
            getString(R.string.server_info, deviceIp, serverPort)
        } else {
            getString(R.string.server_info_unavailable)
        }

        // Theme switch
        dialogBinding.switchTheme.isChecked = isDarkMode
        dialogBinding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        dialogBinding.etDialogUsername.setText(savedUsername)
        dialogBinding.etDialogHost.setText(savedHost)
        dialogBinding.etDialogPort.setText(savedPort.toString())
        dialogBinding.etDialogRoom.setText(savedRoom)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val username = dialogBinding.etDialogUsername.text.toString().ifEmpty { "karsu" }
                val host = dialogBinding.etDialogHost.text.toString().ifEmpty { "127.0.0.1" }
                val port = dialogBinding.etDialogPort.text.toString().toIntOrNull() ?: 8080
                val room = dialogBinding.etDialogRoom.text.toString().ifEmpty { "general" }
                savedUsername = username
                savedHost = host
                savedPort = port
                savedRoom = room
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEmojiPicker() {
        val emojis = arrayOf(
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE0E",
            "\uD83E\uDD14", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE21", "\uD83E\uDD73",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83D\uDCAA",
            "\uD83E\uDD1D", "\u2764\uFE0F", "\uD83D\uDD25", "\u2B50", "\uD83C\uDF89",
            "\u2705", "\u274C", "\uD83D\uDCAF", "\uD83D\uDE80", "\uD83D\uDCA1",
            "\uD83C\uDFB5", "\uD83D\uDCF8", "\uD83C\uDFC6", "\uD83C\uDF1F", "\uD83D\uDCAC",
            "\uD83D\uDE0A", "\uD83D\uDE01", "\uD83E\uDD23", "\uD83D\uDE05", "\uD83D\uDE07",
            "\uD83E\uDD7A", "\uD83D\uDE0F", "\uD83D\uDE34", "\uD83E\uDD2E", "\uD83E\uDD2F",
            "\uD83D\uDC4B", "\u270C\uFE0F", "\uD83E\uDD1E", "\uD83D\uDD90\uFE0F", "\uD83D\uDC40",
            "\uD83D\uDC80", "\uD83E\uDD21", "\uD83D\uDC7B", "\uD83D\uDCA9", "\uD83D\uDE48"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Emoji")
            .setItems(emojis) { _, which ->
                binding.etMessage.text.insert(
                    binding.etMessage.selectionStart.coerceAtLeast(0),
                    emojis[which]
                )
            }
            .show()
    }

    private fun getDeviceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.connectionState.collect { state ->
                        updateConnectionUI(state)
                    }
                }

                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages)
                        if (messages.isNotEmpty()) {
                            binding.recyclerMessages.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }

                launch {
                    viewModel.isRecording.collect { recording ->
                        binding.btnMic.alpha = if (recording) 0.5f else 1.0f
                    }
                }

                launch {
                    viewModel.typingText.collect { text ->
                        binding.tvTypingIndicator.visibility =
                            if (text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                        binding.tvTypingIndicator.text = text
                    }
                }

                launch {
                    viewModel.onlineUsers.collect { users ->
                        if (users.isEmpty()) {
                            binding.tvOnlineUsers.visibility = android.view.View.GONE
                        } else {
                            binding.tvOnlineUsers.visibility = android.view.View.VISIBLE
                            binding.tvOnlineUsers.text = getString(R.string.online_users_count, users.size)
                        }
                    }
                }

                launch {
                    viewModel.dmTarget.collect { target ->
                        binding.etMessage.hint = if (target != null) {
                            "DM to $target..."
                        } else {
                            getString(R.string.message_hint)
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionUI(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                binding.tvStatus.text = getString(R.string.status_disconnected)
                binding.btnConnect.isEnabled = true
                binding.btnDisconnect.isEnabled = false
                binding.btnSend.isEnabled = false
                binding.btnMic.isEnabled = false
                binding.btnSettings.isEnabled = true
            }
            is ConnectionState.Connecting -> {
                binding.tvStatus.text = getString(R.string.status_connecting)
                binding.btnConnect.isEnabled = false
                binding.btnDisconnect.isEnabled = false
                binding.btnSend.isEnabled = false
                binding.btnMic.isEnabled = false
                binding.btnSettings.isEnabled = false
            }
            is ConnectionState.Connected -> {
                binding.tvStatus.text = getString(R.string.status_connected)
                binding.btnConnect.isEnabled = false
                binding.btnDisconnect.isEnabled = true
                binding.btnSend.isEnabled = true
                binding.btnMic.isEnabled = true
                binding.btnSettings.isEnabled = false
            }
            is ConnectionState.Reconnecting -> {
                binding.tvStatus.text = getString(
                    R.string.status_reconnecting, state.attempt, state.maxAttempts
                )
                binding.btnConnect.isEnabled = false
                binding.btnDisconnect.isEnabled = true
                binding.btnSend.isEnabled = false
                binding.btnMic.isEnabled = false
                binding.btnSettings.isEnabled = false
            }
            is ConnectionState.Error -> {
                binding.tvStatus.text = getString(R.string.status_error)
                binding.btnConnect.isEnabled = true
                binding.btnDisconnect.isEnabled = false
                binding.btnSend.isEnabled = false
                binding.btnMic.isEnabled = false
                binding.btnSettings.isEnabled = true

                val msg = when (state.type) {
                    ErrorType.CONNECTION_LOST ->
                        getString(R.string.error_connection_lost, state.detail ?: "")
                    ErrorType.CONNECTION_FAILED ->
                        getString(R.string.error_connection_failed, state.detail ?: "")
                    ErrorType.MESSAGE_FAILED ->
                        getString(R.string.error_message_failed, state.detail ?: "")
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
