# KarSu Chat — Real-Time Multi-Platform Messaging

> [Turkce README / Turkish README](README_TR.md)

A real-time WebSocket chat application with an **embedded Ktor server**, an **Android client**, a **C# WPF desktop client**, and a **browser-based web client** — all communicating over JSON via WebSocket. Supports chat rooms, direct messaging, voice messages, typing indicators, emoji picker, delivery status tracking, and more.

<p align="center">
  <img src="screenshots/explorer_T5QXvZJ2hS.png" alt="Both clients chatting" width="700" />
</p>

<p align="center">
  <img src="screenshots/scrcpy_VNjwPDbF7f.png" alt="Android Client" width="300" />
  <img src="screenshots/ChatClientWpf_tW1L2tylkT.png" alt="C# WPF Client" width="380" />
</p>

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        KTOR SERVER                            │
│        ws://host:8080/chat/{clientId}                         │
│        ws://host:8080/chat/{roomId}/{clientId}                │
│                                                              │
│   Routing.kt ──▶ ConnectionManager                            │
│   (WebSocket)     - addConnection(id, session, roomId)       │
│                   - removeConnection(id)                     │
│                   - broadcastToRoom(roomId, msg, excludeId)  │
│                   - sendTo(clientId, msg)  ← Direct Messages │
│                                                              │
│   GET  /health              → Health check                   │
│   GET  /clients             → Connected client list (JSON)   │
│   GET  /rooms               → Active rooms list              │
│   GET  /rooms/{id}/clients  → Room members                   │
│   GET  /                    → Web chat client (HTML)         │
│   POST /send                → Send message via REST (curl)   │
└──────┬──────────┬──────────────────┬─────────────────────────┘
       │          │                  │
  WebSocket   WebSocket         WebSocket
       │          │                  │
┌──────▼────┐ ┌───▼──────────┐ ┌────▼──────────────────────────┐
│ ANDROID   │ │ C# WPF      │ │ WEB CLIENT                    │
│ (Kotlin)  │ │ (.NET 8)     │ │ (HTML/CSS/JS)                 │
│ MVVM      │ │ MVVM         │ │ Single-page app               │
│           │ │              │ │ Served from Ktor at GET /      │
│ StateFlow │ │ Data Binding │ │ Vanilla JS + Web Audio API    │
│ Koin DI   │ │ RelayCommand │ │                               │
└───────────┘ └──────────────┘ └───────────────────────────────┘
```

## Tech Stack

### Ktor Server (Embedded)
| Technology | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.10 | Language |
| Ktor Server | 3.4.0 | WebSocket server (Netty engine) |
| kotlinx.serialization | 1.10.0 | JSON serialization |
| Koin | 4.0.4 | Dependency injection |
| Logback | 1.5.30 | Logging |

### Android Client
| Technology | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.10 | Language |
| Ktor Client | 3.4.0 | WebSocket client (OkHttp engine) |
| Material 3 | 1.13.0 | UI components & theming |
| Lifecycle | 2.9.1 | ViewModel + StateFlow |
| Koin | 4.0.4 | Dependency injection |
| Coroutines | 1.10.1 | Async operations |
| ViewBinding | — | Type-safe view access |

### C# WPF Client
| Technology | Version | Purpose |
|---|---|---|
| .NET | 8.0 | Runtime |
| WPF | — | Desktop UI framework |
| System.Text.Json | — | JSON serialization |
| ClientWebSocket | — | Native WebSocket client |

## Project Structure

```
KtorWebSocketMVVM/
├── app/                          # Android client module
│   └── src/main/java/.../
│       ├── di/                   # Koin DI module (AppModule)
│       ├── data/
│       │   ├── model/            # ChatMessage, ConnectionState, ErrorType
│       │   ├── remote/           # WebSocketDataSource (Ktor Client)
│       │   ├── audio/            # AudioRecorder, AudioPlayer, SoundEffectManager
│       │   └── repository/       # ChatRepository
│       ├── notification/         # ChatNotificationManager
│       └── ui/chat/              # ChatActivity, ChatViewModel, ChatAdapter
│
├── ktor-server/                  # Embedded Ktor server module
│   └── src/main/kotlin/com/karsu/
│       ├── di/                   # Koin server module
│       ├── model/                # ChatMessage
│       ├── plugins/              # Routing, Sockets, Serialization
│       └── session/              # ConnectionManager (room-aware)
│   └── src/main/resources/
│       └── web/chat.html         # Browser-based web chat client
│
├── csharp-client/                # WPF desktop client
│   ├── Models/                   # ChatMessage
│   ├── ViewModels/               # ChatViewModel, BaseViewModel, RelayCommand
│   ├── Views/                    # MainWindow.xaml
│   └── Services/                 # WebSocketService, SettingsService, AudioRecorderService, Logger
│
└── screenshots/
```

## How It Works

1. **Server starts** embedded inside the Android app on port `8080` (also runnable standalone)
2. **Clients connect** via WebSocket to `ws://<host>:8080/chat/<clientId>` or `ws://<host>:8080/chat/<roomId>/<clientId>`
3. Server sends a **welcome message** to the connecting client
4. When a client sends a message, the server **broadcasts** it to all clients in the same room
5. Direct messages are routed only to the target user via the `sendTo` field
6. The server sends a **delivery acknowledgement** (`status: "delivered"`) back to the sender

### Message Format
```json
{
  "sender": "karsu",
  "content": "Hello!",
  "timestamp": 1771100166330,
  "type": "text",
  "messageId": "a1b2c3d4",
  "sendTo": null,
  "status": "sent",
  "audioData": null,
  "audioDuration": 0
}
```

### Message Flow
```
C# Client: types "Hello!" → Send
  → WebSocket frame → Ktor Server
  → Server broadcasts to room → Android Client + Web Client
  → Server sends delivery ack → C# Client (✓✓)

Android: sends DM to "papa-1" → "Hi!"
  → WebSocket frame → Ktor Server
  → Server routes to papa-1 only (private)
  → papa-1 sees: "[DM] karsu: Hi!"
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Serves the browser-based web chat client |
| `GET` | `/health` | Health check — returns `Server is running` |
| `GET` | `/clients` | Returns connected client IDs as JSON array |
| `GET` | `/rooms` | Returns active room IDs as JSON array |
| `GET` | `/rooms/{roomId}/clients` | Returns client IDs in a specific room |
| `POST` | `/send` | Broadcasts a JSON message to all connected WebSocket clients |
| `WS` | `/chat/{clientId}` | WebSocket endpoint (default "general" room) |
| `WS` | `/chat/{roomId}/{clientId}` | WebSocket endpoint for a specific room |

### WebSocket Connection

Connect to the chat server using the WebSocket protocol:

```
ws://<host>:8080/chat/<clientId>                # joins "general" room
ws://<host>:8080/chat/<roomId>/<clientId>        # joins a specific room
```

- `clientId` — unique identifier for the connecting client (e.g., `android-1`, `csharp-1`, `web-1`)
- `roomId` — chat room to join (messages are scoped to rooms)
- On connect, the server sends a welcome message: `{"sender":"server","content":"Welcome <clientId>!"}`
- Messages are broadcast only to clients in the same room

**Example with [websocat](https://github.com/vi/websocat):**
```bash
websocat ws://192.168.1.120:8080/chat/terminal-1
# Type a JSON message and press Enter:
{"sender":"terminal-1","content":"Hello from terminal!"}

# Join a specific room:
websocat ws://192.168.1.120:8080/chat/dev-team/terminal-1
```

### REST API — Send Message via curl

```bash
# Send a message to all connected clients
curl -X POST http://192.168.1.120:8080/send \
  -H "Content-Type: application/json" \
  -d '{"sender":"curl-user","content":"Hello from curl!"}'

# Check server health
curl http://192.168.1.120:8080/health

# List connected clients (JSON array)
curl http://192.168.1.120:8080/clients

# List active rooms
curl http://192.168.1.120:8080/rooms

# List clients in a specific room
curl http://192.168.1.120:8080/rooms/general/clients
```

## Features

### Messaging
- **WhatsApp-style chat bubbles** — own messages right-aligned, others left-aligned, server messages centered
- **Chat rooms** — create and join different rooms (`/chat/{roomId}/{clientId}`)
- **Direct messages (DM)** — private 1-on-1 messaging via `sendTo` field, routed server-side
- **Voice messages** — record, send, and play audio messages (Base64-encoded, all platforms)
- **Emoji picker** — quick emoji selection popup (50+ emojis, all platforms)
- **Typing indicators** — real-time "X is typing..." with debounce logic
- **Message delivery status** — sent (✓), delivered (✓✓), read (✓✓) tracking with server acknowledgements
- **Enter key** to send messages (all clients)

### Notifications & Audio
- **Push notifications** — incoming message notifications when Android app is in background
- **Sound effects** — audible feedback on message send/receive (ToneGenerator on Android, SystemSounds on C#, Web Audio API on browser)
- **Online users panel** — real-time user count with click-to-view list, HTTP polling every 5 seconds

### UI & Theming
- **Dark / Light theme toggle** — MaterialSwitch (Android), DynamicResource swapping (C# WPF)
- **Custom font** — Montserrat Medium (Android)
- **Persistent settings** — host, port, username, room saved across sessions (all clients)
- **Connection state management** — Connecting, Connected, Disconnected, Error states
- **Auto-reconnect** — automatic reconnection on connection loss (Android & C#)

### Multi-Platform
- **Android** — Kotlin, MVVM, Material 3, Koin DI, Ktor Client
- **C# WPF** — .NET 8, MVVM, ClientWebSocket, dark/light theme
- **Web browser** — single-page HTML/CSS/JS app served from `GET /`, no build step required
- **File-based logging** (C# client)

## Getting Started

### Option 1: Server Embedded in Android
1. Open the project in Android Studio
2. Run the `app` module — the Ktor server starts automatically on port `8080`
3. Connect from any client (C#, web browser, or another Android device)

### Option 2: Standalone Server
```bash
cd ktor-server
./gradlew run
# Server runs at ws://localhost:8080/chat/{clientId}
# Web client available at http://localhost:8080/
```

### Android Client
- **Emulator** → Host: `10.0.2.2`
- **Real device on same network** → Host: your PC's IP address
- Tap the settings icon to configure host, port, username, and room

### C# WPF Client
```bash
cd csharp-client
dotnet run
```
- Enter host IP, port, username, and room in the top bar
- Settings are persisted automatically in `settings.json`
- Toggle dark/light theme with the sun/moon button

### Web Client
- Open `http://<server-ip>:8080/` in any modern browser
- Enter username, configure host/port if needed, and click Connect
- No installation or build step required

## MVVM Layers

### Android (Kotlin)
| Layer | File | Responsibility |
|---|---|---|
| **Model** | `ChatMessage.kt`, `ConnectionState.kt` | Data classes (serializable) |
| **View** | `ChatActivity.kt` + XML layouts | UI rendering, observing StateFlow |
| **ViewModel** | `ChatViewModel.kt` | UI state, user actions, online polling |
| **Repository** | `ChatRepository.kt` | Abstracts data source |
| **DataSource** | `WebSocketDataSource.kt` | Ktor Client WebSocket I/O |
| **Audio** | `AudioRecorder.kt`, `AudioPlayer.kt`, `SoundEffectManager.kt` | Voice recording, playback, sound effects |
| **Notification** | `ChatNotificationManager.kt` | Background message notifications |

### C# WPF (.NET 8)
| Layer | File | Responsibility |
|---|---|---|
| **Model** | `ChatMessage.cs` | Data class with JSON attributes, delivery status |
| **View** | `MainWindow.xaml` | WPF UI with data binding, emoji picker |
| **ViewModel** | `ChatViewModel.cs` | INotifyPropertyChanged, RelayCommand, theme toggle |
| **Service** | `WebSocketService.cs` | ClientWebSocket management |
| **Service** | `SettingsService.cs` | JSON file persistence |
| **Service** | `AudioRecorderService.cs` | Voice message recording |

### Web Client (HTML/CSS/JS)
| Component | Description |
|---|---|
| `chat.html` | Single-page app with embedded CSS/JS |
| WebSocket API | Native browser WebSocket connection |
| Web Audio API | Voice message playback + sound effects |

## Roadmap

### Completed
- [x] Notification for incoming messages (Android background)
- [x] Typing indicator ("typing..." status)
- [x] Online users panel (using `/clients` endpoint)
- [x] Sound effect on message send/receive
- [x] Emoji picker
- [x] Direct messages (private chat using `sendTo`)
- [x] Chat rooms (`/chat/{roomId}/{clientId}`)
- [x] Auto-reconnect on connection loss
- [x] Dark/Light theme toggle (C# client)
- [x] Web client (single-page HTML/CSS/JS)
- [x] Voice messages (record, send, play — all platforms)
- [x] Message delivery status (sent / delivered / read)

### Planned
- [ ] Date grouping in chat ("Today", "Yesterday")
- [ ] Message history with local database (Room / SQLite)
- [ ] Image & file sharing (Base64 / multipart)
- [ ] End-to-end encryption (E2E)
- [ ] User authentication (JWT)
- [ ] Push notifications (Firebase FCM)
- [ ] Voice & video call (WebRTC)
- [ ] Message reactions (emoji reactions on messages)
- [ ] Reply to message (quote + reply)
- [ ] User profile / avatar support
- [ ] Message search / filter
- [ ] Read receipts (per-user read tracking)

## License

This project is licensed under the [MIT License](LICENSE).
