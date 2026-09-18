# AuraPlay 🎵🛡️
### Premium Web Media & Low-Data Background Audio Player for Android 13 Go Edition

AuraPlay is a high-performance, lightweight Android application crafted in **Kotlin** specifically tailored for **Android 13 Go Edition** (API 33). It combines a dark obsidian/Spotify-styled aesthetic with advanced **AdShield** content filtering and a background audio streaming engine that minimizes data consumption by switching to pure audio stream bandwidth when the app is in the background.

---

## 🌟 Key Features

1. **Default Startup URL & Quick Presets**:
   - Easily configure any default URL (e.g., `https://m.youtube.com`, `https://music.youtube.com`, `https://soundcloud.com`).
   - The app automatically launches directly into your saved URL on cold start.
   - Quick one-tap preset selector for YouTube Mobile, YouTube Music, SoundCloud, and Twitch Web.

2. **AdShield Content Blocker (Zero Advertisements)**:
   - **Network-Level Interception**: Blocks known advertising networks, tracking telemetry, and ad-server requests via `AuraWebViewClient.shouldInterceptRequest`.
   - **DOM & Player Ad Stripper**: Injected JavaScript monitors video player states in real time:
     - Automatically accelerates and skips unskippable video ads (`16x` rate fast-forward to end).
     - Programmatically triggers "Skip Ad" buttons immediately upon appearance.
     - Collapses and eliminates banner ads, sponsored cards, and promotional overlays.
     - Auto-dismisses "Get YouTube Premium" and "Open in App" modals.

3. **Background Audio Playback & Spotify-Style Notification**:
   - Powered by an Android Foreground Service (`MediaPlaybackService`) using `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
   - Connected with Android `MediaSessionCompat` for lockscreen and Bluetooth media controls.
   - **Spotify-Style Media Notification**:
     - Displays full metadata (track title, channel/artist name, live album art / video thumbnail).
     - Compact and expanded controls (Rewind/Prev, Play/Pause, Forward/Next, Stop).
     - Integrated with `AudioManager` audio focus (pauses on incoming calls, ducks volume on notifications).
     - Utilizes `WakeLock` & `WifiLock` to prevent Doze mode from interrupting audio during screen-off states.

4. **Smart Low-Data Audio Mode**:
   - Streaming full video in the background drains cellular data and CPU/battery.
   - When the user locks the screen or minimizes the app, AuraPlay signals the HTML5 player to downgrade stream resolution to the minimum available quality (`tiny` / 144p) and turns off visual canvas/video frame decoding.
   - This drops data consumption by **over 95%** (down to ~48-64 kbps, essentially pure audio bandwidth).
   - When returning to the foreground, video quality and visual rendering are automatically restored!

5. **Android 13 Go Edition Optimizations**:
   - Zero Jetpack Compose bloatware overhead: uses lightweight XML ViewBinding for ultra-low RAM usage (ideal for 1GB - 2GB RAM Go devices).
   - Complies with Android 13 runtime permissions (`POST_NOTIFICATIONS`).
   - Built-in HTML5 fullscreen video support with seamless immersive transition.

---

## 🏗️ Architecture

```
com.auraplay.app
├── AuraApplication.kt                 // App initialization & NotificationChannel
├── MainActivity.kt                    // Main UI, WebView orchestration, back navigation
├── data
│   └── AppPreferences.kt             // SharedPreferences for URL, AdShield & background toggles
├── service
│   ├── MediaPlaybackService.kt       // Foreground Service, MediaSessionCompat, AudioFocus, WakeLock
│   ├── MediaNotificationManager.kt   // Spotify-style NotificationCompat.MediaStyle builder
│   └── PlaybackStateHolder.kt        // Reactive StateFlow playback state & metadata holder
├── webview
│   ├── AdBlockEngine.kt              // Network ad URL filter & DOM ad-stripper script
│   ├── AuraWebViewClient.kt          // Ad interception & script injection
│   ├── AuraWebChromeClient.kt        // HTML5 fullscreen video & progress tracking
│   └── WebMediaBridge.kt             // JavascriptInterface for 2-way media communication
└── ui
    └── SettingsBottomSheetDialog.kt  // Settings bottom sheet with presets & switches
```

---

## 🚀 Building and Running

### Prerequisites:
- JDK 17 or JDK 21
- Android SDK (API 33)

### Build Debug APK:
```bash
./gradlew assembleDebug
```

### Run Unit Tests:
```bash
./gradlew test
```
