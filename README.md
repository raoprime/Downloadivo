# Downloadivo Android 🚀
> High-performance universal media downloader for Android inspired by Seal. Built with Jetpack Compose & Material 3.

---

## ✨ Features

- **⚡ 1-Tap Share Sheet:** Share any video/audio link from YouTube, Instagram, X (Twitter), TikTok, Facebook directly into Downloadivo.
- **🛡️ 24/7 Background Foreground Service:** Downloads continue reliably in the background without Android OS battery killers stopping them.
- **📁 Automatic MediaStore Gallery Sync:** Videos are automatically saved to `Movies/Downloadivo` and MP3 audio to `Music/Downloadivo` with zero broad storage permission prompts.
- **🎨 Material 3 Dynamic Theming:** Adapts seamlessly to user wallpaper colors (Material You), with dark/light mode support and zero clutter.
- **🚀 High-Speed Line-Rate Chunk Streaming:** Real-time speed monitor (`MB/s`), progress percentage, and bytes counter.

---

## 🛠️ Tech Stack

- **UI:** Jetpack Compose + Material 3 (Material You)
- **Architecture:** Android Modern Architecture with StateFlow & Coroutines
- **Networking:** OkHttp 4.12 with chunk streaming
- **Image Loading:** Coil 2.7
- **Target SDK:** 36 (Android 15 Ready), Min SDK: 26 (Android 8.0+)
- **Build System:** Gradle 9.1 + JDK 21

---

## 📦 APK Location

After building locally with `./gradlew assembleDebug`, the APK is available at:
```
app/build/outputs/apk/debug/app-debug.apk
```
