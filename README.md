# ⚡ Downloadivo — Universal High-Speed Media Downloader

Downloadivo is a production-grade, high-performance media downloader supporting **8K, 4K, 1080p video, high-bitrate audio, Instagram Reels, Carousels, Stories, Profiles, Spotify Tracks, and YouTube Community posts**.

---

## 🚀 Features

- **8K / 4K / 60FPS / HDR** support with multi-stream FFmpeg merging.
- **Batch Download Queue** with real-time ETA and live speed meters.
- **Instagram Specialist Engine** (Reels, Stories, Carousels, Profile DP).
- **YouTube Specialist Engine** (Videos, Shorts, Playlists, Community Posts, Channel Banner & Avatar).
- **Spotify Cloud Engine** (320kbps MP3 extraction with embedded cover art).
- **Twitter / X, TikTok, Reddit, Pinterest** high-resolution media extraction.
- **Universal Mobile & Remote Browser Streaming** (`/file/{task_id}`).
- **Batch ZIP Packaging** (`/batch-zip`) — 1-click download of all batch items.
- **Zero Downtime Architecture** via Cloudflare Tunnel (Enterprise DDoS protected, HTTPS).
- **100% Free & Forever Zero Cost**.

---

## 🛠️ Quick Start (Windows)

1. **Install Dependencies:**
   ```bash
   pip install -r requirements.txt
   ```
2. **Launch Server:**
   ```bash
   python main.py
   ```
   Or double-click `run_downloadivo.bat`.

3. **Start 24/7 Silent Background Service:**
   Double-click `downloadivo_background_service.vbs` (automatically launches on Windows boot).

---

## 🌐 Public Live Tunnel

Run Cloudflare Tunnel for worldwide instant access:
```bash
start_live_tunnel.bat
```
Your live link (`https://*.trycloudflare.com`) is automatically saved to `tunnel_url.txt` and shown in the Downloadivo header!

---

## 📜 License
MIT License. Open-source and free forever.
