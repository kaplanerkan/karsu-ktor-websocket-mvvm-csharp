# KarSu Chat — Gercek Zamanli Coklu Platform Mesajlasma

> [English README](README.md)

Ktor WebSocket sunucusu, Android istemcisi ve C# WPF masaustu istemcisinden olusan gercek zamanli bir sohbet uygulamasi. Tum iletisim JSON formatinda WebSocket uzerinden gerceklesir.

<p align="center">
  <img src="screenshots/explorer_T5QXvZJ2hS.png" alt="Her iki istemci sohbet halinde" width="700" />
</p>

<p align="center">
  <img src="screenshots/scrcpy_VNjwPDbF7f.png" alt="Android Istemci" width="300" />
  <img src="screenshots/ChatClientWpf_tW1L2tylkT.png" alt="C# WPF Istemci" width="380" />
</p>

## Mimari

```
┌─────────────────────────────────────────────────────────┐
│                     KTOR SUNUCU                         │
│              ws://host:8080/chat/{clientId}              │
│                                                         │
│   Routing.kt ──▶ ConnectionManager                      │
│   (WebSocket)     - addConnection(id, session)          │
│                   - removeConnection(id)                │
│                   - broadcast(msg, excludeId)           │
│                                                         │
│   GET /health     → Saglik kontrolu                     │
│   GET /clients    → Bagli istemci listesi                │
└────────────┬────────────────────┬───────────────────────┘
             │                    │
       WebSocket             WebSocket
             │                    │
┌────────────▼──────────┐ ┌──────▼──────────────────────┐
│   ANDROID ISTEMCI     │ │   C# WPF ISTEMCI            │
│   (Kotlin · MVVM)     │ │   (.NET 8 · MVVM)           │
│                       │ │                              │
│  View (Activity+XML)  │ │  View (MainWindow.xaml)      │
│    ↕ StateFlow        │ │    ↕ Data Binding            │
│  ViewModel            │ │  ViewModel                   │
│    ↕                  │ │    ↕                         │
│  Repository           │ │  WebSocketService            │
│    ↕                  │ │  (ClientWebSocket)           │
│  WebSocketDataSource  │ │                              │
│  (Ktor Client)        │ │  SettingsService (JSON)      │
└───────────────────────┘ └─────────────────────────────┘
```

## Teknoloji Yigini

### Ktor Sunucu (Gomulu)
| Teknoloji | Surum | Amac |
|---|---|---|
| Kotlin | 2.3.10 | Programlama dili |
| Ktor Server | 3.4.0 | WebSocket sunucusu (Netty motoru) |
| kotlinx.serialization | 1.10.0 | JSON serializasyon |
| Koin | 4.0.4 | Dependency injection (bagimlilk enjeksiyonu) |
| Logback | 1.5.30 | Loglama |

### Android Istemci
| Teknoloji | Surum | Amac |
|---|---|---|
| Kotlin | 2.3.10 | Programlama dili |
| Ktor Client | 3.4.0 | WebSocket istemcisi (OkHttp motoru) |
| Material 3 | 1.13.0 | UI bilesenleri ve tema |
| Lifecycle | 2.9.1 | ViewModel + StateFlow |
| Koin | 4.0.4 | Dependency injection |
| Coroutines | 1.10.1 | Asenkron islemler |
| ViewBinding | — | Tip-guvenli view erisimi |

### C# WPF Istemci
| Teknoloji | Surum | Amac |
|---|---|---|
| .NET | 8.0 | Calisma ortami |
| WPF | — | Masaustu UI framework |
| System.Text.Json | — | JSON serializasyon |
| ClientWebSocket | — | Yerel WebSocket istemcisi |

## Proje Yapisi

```
KtorWebSocketMVVM/
├── app/                          # Android istemci modulu
│   └── src/main/java/.../
│       ├── di/                   # Koin DI modulu
│       ├── data/
│       │   ├── model/            # ChatMessage, ConnectionState, ErrorType
│       │   ├── remote/           # WebSocketDataSource (Ktor Client)
│       │   └── repository/       # ChatRepository
│       └── ui/chat/              # ChatActivity, ChatViewModel, ChatAdapter
│
├── ktor-server/                  # Gomulu Ktor sunucu modulu
│   └── src/main/kotlin/com/karsu/
│       ├── di/                   # Koin sunucu modulu
│       ├── model/                # ChatMessage
│       ├── plugins/              # Routing, Sockets, Serialization
│       └── session/              # ConnectionManager
│
├── csharp-client/                # WPF masaustu istemci
│   ├── Models/                   # ChatMessage
│   ├── ViewModels/               # ChatViewModel, BaseViewModel, RelayCommand
│   ├── Views/                    # MainWindow.xaml
│   └── Services/                 # WebSocketService, SettingsService, Logger
│
└── screenshots/
```

## Nasil Calisir?

1. **Sunucu baslar** — Android uygulamasinin icine gomulu olarak `8080` portunda calisir (bagimsiz olarak da calistirilabilir)
2. **Istemciler baglanir** — WebSocket ile `ws://<host>:8080/chat/<clientId>` adresine baglanir
3. Sunucu, baglanan istemciye **karsilama mesaji** gonderir
4. Bir istemci mesaj gonderdiginde, sunucu mesaji bagli diger tum istemcilere **yayinlar** (broadcast)
5. Mesajlar `sender`, `content` ve `timestamp` alanlariyla JSON formatinda kodlanir

### Mesaj Formati
```json
{
  "sender": "karsu",
  "content": "Merhaba!",
  "timestamp": 1771100166330
}
```

### Mesaj Akisi
```
C# Istemci: "Merhaba!" yazar → Gonder
  → WebSocket frame → Ktor Sunucu
  → Sunucu yayinlar → Android Istemci
  → Android ekraninda balon: "papa-1: Merhaba!"

Android: "Selam!" yazar → Gonder
  → WebSocket frame → Ktor Sunucu
  → Sunucu yayinlar → C# Istemci
  → C# ekraninda balon: "karsu: Selam!"
```

## Ozellikler

- **WhatsApp tarzi sohbet balonlari** — kendi mesajlariniz sagda, digerlerininki solda, sunucu mesajlari ortada
- **Karanlik / Aydinlik tema** degistirme (MaterialSwitch ile, Android)
- **Kalici ayarlar** — host, port, kullanici adi oturumlar arasi kaydedilir (her iki istemci)
- **Ozel yazi tipi** — Montserrat Medium (Android)
- **Enter tusu** ile mesaj gonderme (her iki istemci)
- **Baglanti durumu yonetimi** — Baglaniyor, Bagli, Baglanti Kesildi, Hata durumlari
- **Dosya tabanli loglama** (C# istemci)

## Baslarken

### Secenek 1: Sunucu Android Icinde Gomulu
1. Projeyi Android Studio'da acin
2. `app` modulunu calistirin — Ktor sunucusu otomatik olarak `8080` portunda baslar
3. C# istemciyi calistirin: `cd csharp-client && dotnet run`
4. C# istemcisine Android cihazin IP adresini girin ve baglanin

### Secenek 2: Bagimsiz Sunucu
```bash
cd ktor-server
./gradlew run
# Sunucu calisir: ws://localhost:8080/chat/{clientId}
```

### Android Istemci
- **Emulator** → Host: `10.0.2.2`
- **Ayni agdaki gercek cihaz** → Host: bilgisayarinizin IP adresi
- Ayarlar ikonuna tiklayarak host, port ve kullanici adini yapilandiriniz

### C# WPF Istemci
```bash
cd csharp-client
dotnet run
```
- Ust cubuga host IP, port ve kullanici adini girin
- Ayarlar otomatik olarak `settings.json` dosyasina kaydedilir

## MVVM Katmanlari

### Android (Kotlin)
| Katman | Dosya | Gorev |
|---|---|---|
| **Model** | `ChatMessage.kt`, `ConnectionState.kt` | Veri siniflari |
| **View** | `ChatActivity.kt` + XML layout'lar | UI render, StateFlow gozlemleme |
| **ViewModel** | `ChatViewModel.kt` | UI durumu, kullanici aksiyonlari |
| **Repository** | `ChatRepository.kt` | Veri kaynagi soyutlamasi |
| **DataSource** | `WebSocketDataSource.kt` | Ktor Client WebSocket I/O |

### C# WPF (.NET 8)
| Katman | Dosya | Gorev |
|---|---|---|
| **Model** | `ChatMessage.cs` | JSON attribute'lu veri sinifi |
| **View** | `MainWindow.xaml` | WPF UI, data binding |
| **ViewModel** | `ChatViewModel.cs` | INotifyPropertyChanged, RelayCommand |
| **Service** | `WebSocketService.cs` | ClientWebSocket yonetimi |
| **Service** | `SettingsService.cs` | JSON dosya kaliciligi |

## Lisans

Bu proje egitim ve gosterim amaciyla gelistirilmistir.
