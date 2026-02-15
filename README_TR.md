# KarSu Chat — Gercek Zamanli Coklu Platform Mesajlasma

> [English README](README.md)

Ktor WebSocket sunucusu, Android istemcisi, C# WPF masaustu istemcisi ve tarayici tabanli web istemcisinden olusan gercek zamanli bir sohbet uygulamasi. Sohbet odalari, ozel mesajlar, sesli mesajlar, yaziyor gostergesi, emoji secici, iletim durumu takibi ve daha fazlasini destekler.

<p align="center">
  <img src="screenshots/explorer_T5QXvZJ2hS.png" alt="Her iki istemci sohbet halinde" width="700" />
</p>

<p align="center">
  <img src="screenshots/scrcpy_VNjwPDbF7f.png" alt="Android Istemci" width="300" />
  <img src="screenshots/ChatClientWpf_tW1L2tylkT.png" alt="C# WPF Istemci" width="380" />
</p>

## Mimari

```
┌──────────────────────────────────────────────────────────────┐
│                        KTOR SUNUCU                            │
│        ws://host:8080/chat/{clientId}                         │
│        ws://host:8080/chat/{roomId}/{clientId}                │
│                                                              │
│   Routing.kt ──▶ ConnectionManager                            │
│   (WebSocket)     - addConnection(id, session, roomId)       │
│                   - removeConnection(id)                     │
│                   - broadcastToRoom(roomId, msg, excludeId)  │
│                   - sendTo(clientId, msg)  ← Ozel Mesajlar   │
│                                                              │
│   GET  /health              → Saglik kontrolu                │
│   GET  /clients             → Bagli istemci listesi (JSON)   │
│   GET  /rooms               → Aktif oda listesi              │
│   GET  /rooms/{id}/clients  → Oda uyeleri                    │
│   GET  /                    → Web sohbet istemcisi (HTML)    │
│   POST /send                → REST ile mesaj gonder (curl)   │
└──────┬──────────┬──────────────────┬─────────────────────────┘
       │          │                  │
  WebSocket   WebSocket         WebSocket
       │          │                  │
┌──────▼────┐ ┌───▼──────────┐ ┌────▼──────────────────────────┐
│ ANDROID   │ │ C# WPF      │ │ WEB ISTEMCI                   │
│ (Kotlin)  │ │ (.NET 8)     │ │ (HTML/CSS/JS)                 │
│ MVVM      │ │ MVVM         │ │ Tek sayfa uygulama            │
│           │ │              │ │ Ktor'dan GET / ile sunulur     │
│ StateFlow │ │ Data Binding │ │ Vanilla JS + Web Audio API    │
│ Koin DI   │ │ RelayCommand │ │                               │
└───────────┘ └──────────────┘ └───────────────────────────────┘
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
│       ├── di/                   # Koin DI modulu (AppModule)
│       ├── data/
│       │   ├── model/            # ChatMessage, ConnectionState, ErrorType
│       │   ├── remote/           # WebSocketDataSource (Ktor Client)
│       │   ├── audio/            # AudioRecorder, AudioPlayer, SoundEffectManager
│       │   └── repository/       # ChatRepository
│       ├── notification/         # ChatNotificationManager
│       └── ui/chat/              # ChatActivity, ChatViewModel, ChatAdapter
│
├── ktor-server/                  # Gomulu Ktor sunucu modulu
│   └── src/main/kotlin/com/karsu/
│       ├── di/                   # Koin sunucu modulu
│       ├── model/                # ChatMessage
│       ├── plugins/              # Routing, Sockets, Serialization
│       └── session/              # ConnectionManager (oda destekli)
│   └── src/main/resources/
│       └── web/chat.html         # Tarayici tabanli web sohbet istemcisi
│
├── csharp-client/                # WPF masaustu istemci
│   ├── Models/                   # ChatMessage
│   ├── ViewModels/               # ChatViewModel, BaseViewModel, RelayCommand
│   ├── Views/                    # MainWindow.xaml
│   └── Services/                 # WebSocketService, SettingsService, AudioRecorderService, Logger
│
└── screenshots/
```

## Nasil Calisir?

1. **Sunucu baslar** — Android uygulamasinin icine gomulu olarak `8080` portunda calisir (bagimsiz olarak da calistirilabilir)
2. **Istemciler baglanir** — WebSocket ile `ws://<host>:8080/chat/<clientId>` veya `ws://<host>:8080/chat/<roomId>/<clientId>` adresine baglanir
3. Sunucu, baglanan istemciye **karsilama mesaji** gonderir
4. Bir istemci mesaj gonderdiginde, sunucu mesaji ayni odadaki diger tum istemcilere **yayinlar**
5. Ozel mesajlar (DM) yalnizca hedef kullaniciya `sendTo` alani ile yonlendirilir
6. Sunucu, gonderene **iletim onayi** (`status: "delivered"`) geri gonderir

### Mesaj Formati
```json
{
  "sender": "karsu",
  "content": "Merhaba!",
  "timestamp": 1771100166330,
  "type": "text",
  "messageId": "a1b2c3d4",
  "sendTo": null,
  "status": "sent",
  "audioData": null,
  "audioDuration": 0
}
```

### Mesaj Akisi
```
C# Istemci: "Merhaba!" yazar → Gonder
  → WebSocket frame → Ktor Sunucu
  → Sunucu odaya yayinlar → Android + Web Istemci
  → Sunucu iletim onayi gonderir → C# Istemci (✓✓)

Android: "papa-1"e DM gonderir → "Selam!"
  → WebSocket frame → Ktor Sunucu
  → Sunucu yalnizca papa-1'e yonlendirir (ozel)
  → papa-1 gorur: "[DM] karsu: Selam!"
```

## API Endpoint'leri

| Metod | Endpoint | Aciklama |
|---|---|---|
| `GET` | `/` | Tarayici tabanli web sohbet istemcisini sunar |
| `GET` | `/health` | Saglik kontrolu — `Server is running` doner |
| `GET` | `/clients` | Bagli istemci ID'lerini JSON dizisi olarak doner |
| `GET` | `/rooms` | Aktif oda ID'lerini JSON dizisi olarak doner |
| `GET` | `/rooms/{roomId}/clients` | Belirli bir odadaki istemci ID'lerini doner |
| `POST` | `/send` | Bagli tum WebSocket istemcilerine JSON mesaj yayinlar |
| `WS` | `/chat/{clientId}` | WebSocket endpoint'i (varsayilan "general" odasi) |
| `WS` | `/chat/{roomId}/{clientId}` | Belirli bir oda icin WebSocket endpoint'i |

### WebSocket Baglantisi

WebSocket protokolu ile sohbet sunucusuna baglanin:

```
ws://<host>:8080/chat/<clientId>                # "general" odasina katilir
ws://<host>:8080/chat/<roomId>/<clientId>        # belirli bir odaya katilir
```

- `clientId` — baglanan istemci icin benzersiz kimlik (ornek: `android-1`, `csharp-1`, `web-1`)
- `roomId` — katilacak sohbet odasi (mesajlar odalara gore gruplanir)
- Baglantiginda sunucu karsilama mesaji gonderir: `{"sender":"server","content":"Welcome <clientId>!"}`
- Mesajlar yalnizca ayni odadaki istemcilere yayinlanir

**[websocat](https://github.com/vi/websocat) ile ornek:**
```bash
websocat ws://192.168.1.120:8080/chat/terminal-1
# Bir JSON mesaj yazin ve Enter'a basin:
{"sender":"terminal-1","content":"Terminalden merhaba!"}

# Belirli bir odaya katilma:
websocat ws://192.168.1.120:8080/chat/dev-team/terminal-1
```

### REST API — curl ile Mesaj Gonderme

```bash
# Tum bagli istemcilere mesaj gonder
curl -X POST http://192.168.1.120:8080/send \
  -H "Content-Type: application/json" \
  -d '{"sender":"curl-user","content":"curl ile merhaba!"}'

# Sunucu saglik kontrolu
curl http://192.168.1.120:8080/health

# Bagli istemcileri listele (JSON dizisi)
curl http://192.168.1.120:8080/clients

# Aktif odalari listele
curl http://192.168.1.120:8080/rooms

# Belirli bir odadaki istemcileri listele
curl http://192.168.1.120:8080/rooms/general/clients
```

## Ozellikler

### Mesajlasma
- **WhatsApp tarzi sohbet balonlari** — kendi mesajlariniz sagda, digerlerininki solda, sunucu mesajlari ortada
- **Sohbet odalari** — farkli odalara katilma ve olusturma (`/chat/{roomId}/{clientId}`)
- **Ozel mesajlar (DM)** — `sendTo` alani ile birebir ozel mesajlasma, sunucu tarafinda yonlendirme
- **Sesli mesajlar** — ses kaydi, gonderme ve oynatma (Base64 kodlu, tum platformlar)
- **Emoji secici** — hizli emoji secim paneli (50+ emoji, tum platformlar)
- **Yaziyor gostergesi** — gercek zamanli "X yaziyor..." debounce mantigi ile
- **Mesaj iletim durumu** — gonderildi (✓), iletildi (✓✓), okundu (✓✓) takibi, sunucu onayi ile
- **Enter tusu** ile mesaj gonderme (tum istemciler)

### Bildirimler ve Ses
- **Push bildirimleri** — Android uygulamasi arka plandayken gelen mesaj bildirimleri
- **Ses efektleri** — mesaj gonderme/alma sesli geri bildirimi (Android: ToneGenerator, C#: SystemSounds, Web: Web Audio API)
- **Cevrimici kullanicilar paneli** — gercek zamanli kullanici sayisi, tiklayinca liste gorunumu, 5 saniyede bir HTTP polling

### Arayuz ve Tema
- **Karanlik / Aydinlik tema degistirme** — MaterialSwitch (Android), DynamicResource degisimi (C# WPF)
- **Ozel yazi tipi** — Montserrat Medium (Android)
- **Kalici ayarlar** — host, port, kullanici adi, oda oturumlar arasi kaydedilir (tum istemciler)
- **Baglanti durumu yonetimi** — Baglaniyor, Bagli, Baglanti Kesildi, Hata durumlari
- **Otomatik yeniden baglanti** — baglanti kopmasinda otomatik yeniden baglanma (Android ve C#)

### Coklu Platform
- **Android** — Kotlin, MVVM, Material 3, Koin DI, Ktor Client
- **C# WPF** — .NET 8, MVVM, ClientWebSocket, karanlik/aydinlik tema
- **Web tarayici** — Ktor'dan `GET /` ile sunulan tek sayfa HTML/CSS/JS uygulamasi, derleme gerektirmez
- **Dosya tabanli loglama** (C# istemci)

## Baslarken

### Secenek 1: Sunucu Android Icinde Gomulu
1. Projeyi Android Studio'da acin
2. `app` modulunu calistirin — Ktor sunucusu otomatik olarak `8080` portunda baslar
3. Herhangi bir istemciden baglanin (C#, web tarayici veya baska bir Android cihaz)

### Secenek 2: Bagimsiz Sunucu
```bash
cd ktor-server
./gradlew run
# Sunucu calisir: ws://localhost:8080/chat/{clientId}
# Web istemci adresi: http://localhost:8080/
```

### Android Istemci
- **Emulator** → Host: `10.0.2.2`
- **Ayni agdaki gercek cihaz** → Host: bilgisayarinizin IP adresi
- Ayarlar ikonuna tiklayarak host, port, kullanici adi ve oda yapilandiriniz

### C# WPF Istemci
```bash
cd csharp-client
dotnet run
```
- Ust cubuga host IP, port, kullanici adi ve oda bilgilerini girin
- Ayarlar otomatik olarak `settings.json` dosyasina kaydedilir
- Gunes/ay butonu ile karanlik/aydinlik tema arasinda gecis yapin

### Web Istemci
- Herhangi bir modern tarayicida `http://<sunucu-ip>:8080/` adresini acin
- Kullanici adini girin, gerekirse host/port ayarlayin ve Baglan'a tiklayin
- Kurulum veya derleme gerektirmez

## MVVM Katmanlari

### Android (Kotlin)
| Katman | Dosya | Gorev |
|---|---|---|
| **Model** | `ChatMessage.kt`, `ConnectionState.kt` | Veri siniflari (serializable) |
| **View** | `ChatActivity.kt` + XML layout'lar | UI render, StateFlow gozlemleme |
| **ViewModel** | `ChatViewModel.kt` | UI durumu, kullanici aksiyonlari, online polling |
| **Repository** | `ChatRepository.kt` | Veri kaynagi soyutlamasi |
| **DataSource** | `WebSocketDataSource.kt` | Ktor Client WebSocket I/O |
| **Audio** | `AudioRecorder.kt`, `AudioPlayer.kt`, `SoundEffectManager.kt` | Ses kaydi, oynatma, ses efektleri |
| **Bildirim** | `ChatNotificationManager.kt` | Arka plan mesaj bildirimleri |

### C# WPF (.NET 8)
| Katman | Dosya | Gorev |
|---|---|---|
| **Model** | `ChatMessage.cs` | JSON attribute'lu veri sinifi, iletim durumu |
| **View** | `MainWindow.xaml` | WPF UI, data binding, emoji secici |
| **ViewModel** | `ChatViewModel.cs` | INotifyPropertyChanged, RelayCommand, tema degistirme |
| **Service** | `WebSocketService.cs` | ClientWebSocket yonetimi |
| **Service** | `SettingsService.cs` | JSON dosya kaliciligi |
| **Service** | `AudioRecorderService.cs` | Sesli mesaj kaydi |

### Web Istemci (HTML/CSS/JS)
| Bilesen | Aciklama |
|---|---|
| `chat.html` | Gomulu CSS/JS ile tek sayfa uygulama |
| WebSocket API | Yerel tarayici WebSocket baglantisi |
| Web Audio API | Sesli mesaj oynatma + ses efektleri |

## Yol Haritasi

### Tamamlanan
- [x] Gelen mesaj bildirimi (Android arka plan)
- [x] Yaziyor gostergesi ("yaziyor..." durumu)
- [x] Cevrimici kullanici paneli (`/clients` endpoint'i ile)
- [x] Mesaj gonderme/alma ses efekti
- [x] Emoji secici
- [x] Ozel mesaj (DM — `sendTo` ile birebir sohbet)
- [x] Sohbet odalari (`/chat/{roomId}/{clientId}`)
- [x] Baglanti kopmasinda otomatik yeniden baglanti
- [x] Karanlik/Aydinlik tema degistirme (C# istemci)
- [x] Web istemci (tek sayfa HTML/CSS/JS)
- [x] Sesli mesajlar (kayit, gonderme, oynatma — tum platformlar)
- [x] Mesaj iletim durumu (gonderildi / iletildi / okundu)

### Planlanan
- [ ] Sohbette tarih gruplama ("Bugun", "Dun")
- [ ] Yerel veritabani ile mesaj gecmisi (Room / SQLite)
- [ ] Gorsel ve dosya paylasimi (Base64 / multipart)
- [ ] Uctan uca sifreleme (E2E)
- [ ] Kullanici dogrulama (JWT)
- [ ] Push bildirim (Firebase FCM)
- [ ] Sesli ve goruntulu arama (WebRTC)
- [ ] Mesaj reaksiyonlari (mesajlara emoji tepkisi)
- [ ] Mesaja yanit verme (alinti + yanit)
- [ ] Kullanici profili / avatar destegi
- [ ] Mesaj arama / filtreleme
- [ ] Okundu bilgisi (kullanici bazinda okunma takibi)

## Lisans

Bu proje [MIT Lisansi](LICENSE) ile lisanslanmistir.
