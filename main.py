"""
FastDownloader Studio Multi-Engine Backend
Engine 1: Instaloader Pro (Instagram Reels, Photos, Carousels, Stories)
Engine 2: Spotify Cloud Engine (Spotify Track/Album to 320kbps MP3 + Album Art)
Engine 3: yt-dlp Core Engine (YouTube Videos, Shorts, Playlists, X/Twitter, TikTok, Reddit, FB)
Engine 4: Direct Stream Engine (Direct media files via chunked HTTP stream)
"""

import os
import re
import sys
import shutil
import threading
import uuid
import webbrowser
import urllib.request
import json
import subprocess
import time
import zipfile
import concurrent.futures
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException, Request, BackgroundTasks
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse, StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator
import uvicorn
import yt_dlp
import instaloader

# ──────────────────────────────────────────────────────────────
# Storage & Directories
# ──────────────────────────────────────────────────────────────
DOWNLOAD_DIR = Path(os.environ.get("DOWNLOAD_DIR", Path.home() / "Downloads" / "Downloadivo"))
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
ASSETS_DIR = Path(__file__).parent / "assets"
ASSETS_DIR.mkdir(parents=True, exist_ok=True)

_NODE_PATH = shutil.which("node") or shutil.which("node.exe") or ""
_FFMPEG_PATH = shutil.which("ffmpeg") or shutil.which("ffmpeg.exe") or ""

# Storage Configuration
# Ephemeral pruning enabled on cloud/headless or if explicitly set via EPHEMERAL_STORAGE=1
IS_EPHEMERAL = os.environ.get("EPHEMERAL_STORAGE", "0") == "1" or not hasattr(os, "startfile")
FILE_RETENTION_SECONDS = int(os.environ.get("FILE_RETENTION_SECONDS", 1800))

# ──────────────────────────────────────────────────────────────
# Windows System Wake-Lock & Power Guard (24/7 Resilience)
# ──────────────────────────────────────────────────────────────
def _enable_system_wakelock():
    """Prevents Windows from suspending CPU/network while service is active."""
    try:
        if sys.platform == "win32":
            import ctypes
            # ES_CONTINUOUS (0x80000000) | ES_SYSTEM_REQUIRED (0x00000001)
            ctypes.windll.kernel32.SetThreadExecutionState(0x80000001)
            print("[INFO] Windows system wake-lock active (24/7 background mode)")
    except Exception as e:
        print(f"[WARN] Wake-lock could not be set: {e}")

_enable_system_wakelock()

# ──────────────────────────────────────────────────────────────
# In-Memory Rate Limiter (Token Bucket / Anti-Abuse)
# ──────────────────────────────────────────────────────────────
_request_counts: Dict[str, List[float]] = {}
_rate_limit_lock = threading.Lock()

def _check_rate_limit(client_ip: str, max_requests: int = 50, window_seconds: int = 60) -> bool:
    """Sliding-window rate limiter per client IP address."""
    if client_ip in ["127.0.0.1", "localhost", "::1"]:
        return True
    now = time.time()
    with _rate_limit_lock:
        timestamps = _request_counts.get(client_ip, [])
        valid_ts = [t for t in timestamps if now - t < window_seconds]
        if len(valid_ts) >= max_requests:
            _request_counts[client_ip] = valid_ts
            return False
        valid_ts.append(now)
        _request_counts[client_ip] = valid_ts
        if len(_request_counts) > 1000:
            for ip in list(_request_counts.keys()):
                _request_counts[ip] = [t for t in _request_counts[ip] if now - t < window_seconds]
                if not _request_counts[ip]:
                    del _request_counts[ip]
        return True

_cached_tunnel_url = ""
_tunnel_lock = threading.Lock()
_last_cf_check = 0.0

def _get_public_tunnel_url() -> str:
    global _cached_tunnel_url, _last_cf_check
    now = time.time()
    with _tunnel_lock:
        if _cached_tunnel_url and (now - _last_cf_check < 15):
            return _cached_tunnel_url
        _last_cf_check = now

    url_file = Path(__file__).parent / "tunnel_url.txt"
    cf_log = Path(__file__).parent / "cloudflared.log"

    # Prefer the active live URL from cloudflared.log
    if cf_log.exists():
        try:
            content = cf_log.read_text("utf-8", errors="ignore")
            matches = re.findall(r"https://[a-zA-Z0-9\-]+\.trycloudflare\.com", content)
            if matches:
                url = matches[-1]
                with _tunnel_lock:
                    _cached_tunnel_url = url
                try:
                    url_file.write_text(url + "\n", encoding="utf-8")
                except Exception:
                    pass
                return url
        except Exception:
            pass

    if url_file.exists():
        try:
            txt = url_file.read_text("utf-8").strip()
            if txt.startswith("http"):
                with _tunnel_lock:
                    _cached_tunnel_url = txt
                return txt
        except Exception:
            pass
    return ""

def _cleanup_old_files_daemon():
    """Background storage pruner and tunnel sync daemon."""
    while True:
        try:
            time.sleep(15)  # Quick check every 15 seconds
            now = time.time()
            
            # Sync tunnel URL if needed
            _get_public_tunnel_url()

            if DOWNLOAD_DIR.exists():
                for f in DOWNLOAD_DIR.glob("*"):
                    if not f.is_file() or f.name.endswith(".gitkeep"):
                        continue
                    try:
                        age = now - f.stat().st_mtime
                        # 1. Prune orphaned partial temp files older than 30 mins
                        if (f.name.endswith(".part") or f.name.endswith(".ytdl")) and age > 1800:
                            f.unlink(missing_ok=True)
                        # 2. Only prune finished media files in cloud / ephemeral deployments
                        elif IS_EPHEMERAL and age > FILE_RETENTION_SECONDS:
                            f.unlink(missing_ok=True)
                    except Exception:
                        pass
        except Exception:
            pass

_cleaner_thread = threading.Thread(target=_cleanup_old_files_daemon, daemon=True, name="storage_cleaner")
_cleaner_thread.start()

app = FastAPI(title="Downloadivo Multi-Engine", version="5.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/assets", StaticFiles(directory=str(ASSETS_DIR)), name="assets")


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"ok": False, "detail": exc.detail, "error": exc.detail, "status_code": exc.status_code},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    err_msg = str(exc)
    for prefix in ["ERROR: ", "[youtube] ", "[generic] ", "[Instagram] "]:
        err_msg = err_msg.replace(prefix, "")
    if "; please report this issue" in err_msg:
        err_msg = err_msg.split("; please report this issue")[0].strip()
    return JSONResponse(
        status_code=500,
        content={"ok": False, "detail": err_msg[:250], "error": "Internal processing error", "status_code": 500},
    )

# Active task state: {task_id: {status, progress, filename, speed, eta, error, ...}}
downloads: Dict[str, dict] = {}
_lock = threading.Lock()

# Worker thread pool: Controlled concurrency (max 3 concurrent active streams)
# Ensures maximum line speed per download, prevents rate limits & socket exhaustion
_executor = concurrent.futures.ThreadPoolExecutor(max_workers=3, thread_name_prefix="dl_worker")


# ──────────────────────────────────────────────────────────────
# Pydantic Schemas
# ──────────────────────────────────────────────────────────────
class DownloadRequest(BaseModel):
    url: str = Field(..., max_length=2048)
    format: str = Field(default="best", max_length=64)  # "best" | "1080p" | "720p" | "480p" | "mp3" | "thumbnail"
    start_time: Optional[str] = Field(default=None, max_length=32)
    end_time: Optional[str] = Field(default=None, max_length=32)

    @field_validator("url")
    @classmethod
    def validate_url(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("URL cannot be empty")
        return v

    @field_validator("format")
    @classmethod
    def validate_format(cls, v: str) -> str:
        return v.strip().lower() or "best"

    @field_validator("start_time", "end_time")
    @classmethod
    def validate_timestamps(cls, v: Optional[str]) -> Optional[str]:
        if not v:
            return None
        v = v.strip()
        if re.match(r"^(?:\d{1,2}:)?\d{1,2}:\d{2}$|^\d+(?:\.\d+)?$", v):
            return v
        return None


class InfoRequest(BaseModel):
    url: str = Field(..., max_length=2048)

    @field_validator("url")
    @classmethod
    def validate_url(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("URL cannot be empty")
        return v


class FileActionRequest(BaseModel):
    filename: Optional[str] = Field(default=None, max_length=255)
    task_id: Optional[str] = Field(default=None, max_length=128)


# ──────────────────────────────────────────────────────────────
# Helper Utilities
# ──────────────────────────────────────────────────────────────
def _clean_filename(text: str) -> str:
    return "".join(c for c in text if c.isalnum() or c in (" ", "-", "_", ".", "(", ")")).strip()[:90]


def _format_size(num_bytes: int) -> str:
    if not num_bytes:
        return "0 B"
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if abs(num_bytes) < 1024.0:
            return f"{num_bytes:3.1f} {unit}"
        num_bytes /= 1024.0
    return f"{num_bytes:.1f} PB"


def _format_duration(seconds: float) -> str:
    if seconds < 60:
        return f"{seconds:.1f}s"
    minutes = int(seconds // 60)
    rem_sec = int(seconds % 60)
    return f"{minutes}m {rem_sec}s"


def _format_speed(b_per_s: float) -> str:
    """Format speed in bytes/sec to human readable format with 1 decimal."""
    if not b_per_s or b_per_s <= 0:
        return ""
    if b_per_s >= 1024 * 1024 * 1024:
        return f"{b_per_s / (1024 * 1024 * 1024):.2f} GB/s"
    if b_per_s >= 1024 * 1024:
        return f"{b_per_s / (1024 * 1024):.1f} MB/s"
    if b_per_s >= 1024:
        return f"{b_per_s / 1024:.0f} KB/s"
    return f"{int(b_per_s)} B/s"


def _format_eta(seconds: float) -> str:
    """Format ETA seconds to a stable MM:SS or HH:MM:SS string."""
    if not seconds or seconds <= 0 or seconds > 86400:
        return ""
    s = int(seconds)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return f"{h:02d}:{m:02d}:{s:02d}" if h else f"{m:02d}:{s:02d}"


def _extract_urls(text: str) -> List[str]:
    """Extract and clean all valid http/https URLs from any text or block."""
    raw_urls = re.findall(r"https?://[^\s,;\"'<>]+", text)
    cleaned = []
    for u in raw_urls:
        u = u.rstrip(".,;)>]\"'")
        if u and u not in cleaned:
            cleaned.append(u)
    return cleaned


def _is_remote_request(request: Request) -> bool:
    """Accurately identify if the request originates from a remote/public client."""
    if not request:
        return False
    cf_ip = request.headers.get("cf-connecting-ip")
    host_hdr = request.headers.get("host", "").lower()
    if cf_ip or "trycloudflare.com" in host_hdr:
        return True
    xff = request.headers.get("x-forwarded-for")
    if xff:
        first_ip = xff.split(",")[0].strip()
        if first_ip not in ["127.0.0.1", "::1", "localhost"] and not first_ip.startswith("192.168.") and not first_ip.startswith("10."):
            return True
    client_ip = request.client.host if request.client else "127.0.0.1"
    return client_ip not in ["127.0.0.1", "::1", "localhost"] and not client_ip.startswith("192.168.") and not client_ip.startswith("10.")


def _create_task(url: str, fmt: str) -> str:
    """Create task record in thread-safe memory registry with LRU cleanup."""
    task_id = str(uuid.uuid4())
    start_ts = time.time()
    with _lock:
        downloads[task_id] = {
            "task_id": task_id,
            "status": "queued",
            "progress": 0,
            "filename": "",
            "speed": "In Queue",
            "eta": "",
            "error": "",
            "url": url,
            "format": fmt,
            "is_audio": fmt == "mp3",
            "start_ts": start_ts,
            "started_at": datetime.now().strftime("%H:%M:%S"),
            "elapsed_sec": 0.0,
            "elapsed_str": "0.0s",
        }
        # In-Memory LRU Cleanup: If tasks exceed 300, safely prune oldest finished
        if len(downloads) > 300:
            completed_keys = [k for k, v in downloads.items() if v.get("status") in ["done", "error", "cancelled"]]
            for k in completed_keys[:60]:
                downloads.pop(k, None)
    return task_id


def _run_task_worker(task_id: str, url: str, fmt: str, start_time: Optional[str] = None, end_time: Optional[str] = None):
    """Worker launcher executed inside thread pool."""
    with _lock:
        if task_id in downloads:
            downloads[task_id]["status"] = "starting"
            downloads[task_id]["speed"] = "Connecting..."
            downloads[task_id]["start_ts"] = time.time()
            if start_time and end_time:
                downloads[task_id]["is_trimmed"] = True
                downloads[task_id]["trim_range"] = f"{start_time} - {end_time}"
    master_download_worker(task_id, url, fmt, start_time, end_time)


def _finish_task(task_id: str, updates: dict):
    with _lock:
        if task_id not in downloads:
            return
        start_ts = downloads[task_id].get("start_ts", time.time())
        elapsed = max(0.1, time.time() - start_ts)
        updates["status"] = "done"
        updates["progress"] = 100
        updates["elapsed_sec"] = round(elapsed, 1)
        updates["elapsed_str"] = _format_duration(elapsed)
        updates["completed_at"] = datetime.now().strftime("%H:%M:%S")
        downloads[task_id].update(updates)


# ──────────────────────────────────────────────────────────────
# ENGINE 1: INSTAGRAM SPECIALIST (INSTALOADER PRO)
# ──────────────────────────────────────────────────────────────
_insta_loader = instaloader.Instaloader(
    dirname_pattern=str(DOWNLOAD_DIR),
    filename_pattern="Instagram_{shortcode}_{date_utc}",
    download_video_thumbnails=False,
    download_geotags=False,
    download_comments=False,
    save_metadata=False,
    post_metadata_txt_pattern="",
)

def _get_instagram_shortcode(url: str) -> Optional[str]:
    # Strictly match post/reel shortcodes: /p/<id>, /reel/<id>, /reels/<id>, /share/p/<id>, /tv/<id>
    match = re.search(r"instagram\.com/(?:[a-zA-Z0-9_\.]+/)?(?:p|reel|reels|tv|share/p|share/reel)/([A-Za-z0-9_-]+)", url)
    if match:
        return match.group(1)
    return None


def _download_instagram(task_id: str, url: str, fmt: str):
    shortcode = _get_instagram_shortcode(url) or "post"
    downloads[task_id].update({
        "status": "downloading",
        "progress": 20,
        "speed": "Instagram Engine",
        "eta": "Resolving media",
    })

    # Strategy 1: yt-dlp InstagramIE extractor (Handles Reels, Photos, and Multi-item Carousels)
    try:
        ydl = yt_dlp.YoutubeDL({'quiet': True, 'skip_download': True, 'no_warnings': True})
        ie = yt_dlp.extractor.instagram.InstagramIE(ydl)
        info = ie.extract(url)

        post_id = info.get("id") or shortcode
        author = info.get("uploader") or info.get("channel") or "instagram"
        title = info.get("title") or f"Instagram Post by @{author}"
        entries = info.get("entries") or [info]
        total_items = len(entries)

        # 1. Thumbnail requested
        if fmt == "thumbnail":
            first_entry = entries[0]
            thumbs = first_entry.get("thumbnails", [])
            thumb_url = thumbs[-1]["url"] if thumbs else (info.get("thumbnail") or "")
            if not thumb_url:
                raise Exception("Cover photo not available")
            
            cover_path = DOWNLOAD_DIR / f"Instagram_{post_id}_Cover.jpg"
            req = urllib.request.Request(thumb_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=15) as resp, open(cover_path, "wb") as f:
                f.write(resp.read())
            
            _finish_task(task_id, {
                "filename": cover_path.name,
                "file_path": str(cover_path),
                "size_str": _format_size(cover_path.stat().st_size),
                "is_audio": False,
                "title": f"Cover by @{author}",
                "thumbnail": thumb_url,
            })
            return

        # 2. Single Video / Reel
        if total_items == 1 and entries[0].get("formats"):
            best_fmt = entries[0]["formats"][-1]
            vid_url = best_fmt["url"]
            ext = best_fmt.get("ext", "mp4")
            raw_file = DOWNLOAD_DIR / f"Instagram_{post_id}.{ext}"

            downloads[task_id].update({"progress": 45, "speed": "Streaming video..."})
            req = urllib.request.Request(vid_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=25) as resp, open(raw_file, "wb") as f:
                f.write(resp.read())

            if fmt == "mp3" and _FFMPEG_PATH:
                downloads[task_id].update({"progress": 85, "speed": "Extracting 320kbps MP3..."})
                mp3_path = raw_file.with_suffix(".mp3")
                cmd = [_FFMPEG_PATH, "-y", "-i", str(raw_file), "-vn", "-b:a", "320k", str(mp3_path)]
                subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if mp3_path.exists():
                    try:
                        raw_file.unlink()
                    except Exception:
                        pass
                    raw_file = mp3_path

            _finish_task(task_id, {
                "filename": raw_file.name,
                "file_path": str(raw_file),
                "size_str": _format_size(raw_file.stat().st_size),
                "is_audio": raw_file.suffix.lower() == ".mp3",
                "title": title,
                "thumbnail": entries[0].get("thumbnails", [{}])[-1].get("url", ""),
            })
            return

        # 3. Single Photo
        if total_items == 1 and not entries[0].get("formats"):
            thumbs = entries[0].get("thumbnails", [])
            if not thumbs:
                raise Exception("No photo stream available")
            img_url = thumbs[-1]["url"]
            out_file = DOWNLOAD_DIR / f"Instagram_{post_id}.jpg"

            downloads[task_id].update({"progress": 50, "speed": "Downloading HD photo..."})
            req = urllib.request.Request(img_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=15) as resp, open(out_file, "wb") as f:
                f.write(resp.read())

            _finish_task(task_id, {
                "filename": out_file.name,
                "file_path": str(out_file),
                "size_str": _format_size(out_file.stat().st_size),
                "is_audio": False,
                "title": f"Photo by @{author}",
                "thumbnail": img_url,
            })
            return

        # 4. Multi-item Carousel (multiple photos / videos)
        saved_files = []
        for i, entry in enumerate(entries, 1):
            pct = 25 + int((i / total_items) * 60)
            downloads[task_id].update({
                "progress": pct,
                "speed": f"Saving item {i} of {total_items}...",
            })
            fmts = entry.get("formats", [])
            thumbs = entry.get("thumbnails", [])
            if fmts:
                media_url = fmts[-1]["url"]
                ext = fmts[-1].get("ext", "mp4")
            elif thumbs:
                media_url = thumbs[-1]["url"]
                ext = "jpg"
            else:
                continue

            item_path = DOWNLOAD_DIR / f"Instagram_{post_id}_{i}.{ext}"
            req = urllib.request.Request(media_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=20) as resp, open(item_path, "wb") as f:
                f.write(resp.read())
            saved_files.append(item_path)

        if not saved_files:
            raise Exception("No carousel media items could be saved")

        # Create zip package
        zip_path = DOWNLOAD_DIR / f"Instagram_{post_id}_Carousel.zip"
        downloads[task_id].update({"progress": 92, "speed": "Packaging carousel ZIP..."})
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for sf in saved_files:
                zf.write(sf, arcname=sf.name)

        _finish_task(task_id, {
            "filename": zip_path.name,
            "file_path": str(zip_path),
            "size_str": _format_size(zip_path.stat().st_size),
            "is_audio": False,
            "title": f"Post by @{author} ({len(saved_files)} items)",
            "thumbnail": entries[0].get("thumbnails", [{}])[-1].get("url", ""),
        })
        return

    except Exception as e_ie:
        # Strategy 2: Instaloader Pro fallback
        try:
            if not shortcode:
                raise e_ie
            post = instaloader.Post.from_shortcode(_insta_loader.context, shortcode)
            owner = post.owner_username or "user"
            before_files = set(DOWNLOAD_DIR.glob("*"))
            _insta_loader.download_post(post, target=f"Instagram_{shortcode}")
            after_files = set(DOWNLOAD_DIR.glob("*"))
            new_files = [f for f in (after_files - before_files) if f.is_file() and not f.name.endswith(".json.xz")]
            if not new_files:
                new_files = [f for f in DOWNLOAD_DIR.glob(f"*{shortcode}*") if f.is_file() and not f.name.endswith(".json.xz")]
            if new_files:
                chosen = new_files[0]
                _finish_task(task_id, {
                    "filename": chosen.name,
                    "file_path": str(chosen),
                    "size_str": _format_size(chosen.stat().st_size),
                    "is_audio": False,
                    "title": f"Instagram @{owner}",
                    "thumbnail": post.url,
                })
                return
        except Exception:
            pass

        # If it was an InstagramIE issue, raise clean actionable message
        err_msg = str(e_ie).replace("ERROR: ", "").replace("[Instagram] ", "")
        raise Exception(f"Instagram: {err_msg}")


# ──────────────────────────────────────────────────────────────
# ENGINE 2: SPOTIFY ENGINE (SPOTIFY OEMBED + YOUTUBE MUSIC)
# ──────────────────────────────────────────────────────────────
def _download_spotify(task_id: str, url: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 15, "speed": "Spotify Engine", "eta": ""})
    try:
        req = urllib.request.Request(f"https://open.spotify.com/oembed?url={url}", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            sdata = json.loads(resp.read().decode())
            track_title = sdata.get("title") or "Spotify Song"
            thumb = sdata.get("thumbnail_url") or ""
    except Exception:
        track_title = "Spotify Song"
        thumb = ""

    if fmt == "thumbnail" and thumb:
        safe_name = _clean_filename(track_title)
        img_path = DOWNLOAD_DIR / f"{safe_name}_Cover.jpg"
        req_img = urllib.request.Request(thumb, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req_img, timeout=10) as r_img, open(img_path, "wb") as f_img:
            f_img.write(r_img.read())
        _finish_task(task_id, {
            "filename": img_path.name,
            "file_path": str(img_path),
            "size_str": _format_size(img_path.stat().st_size),
            "is_audio": False,
            "title": track_title,
            "thumbnail": thumb,
        })
        return

    # Download 320kbps MP3 via ytsearch pipeline
    _download_ytdlp(task_id, f"ytsearch1:{track_title} audio", "mp3")


# ──────────────────────────────────────────────────────────────
# ENGINE 3: DIRECT STREAM ENGINE (HTTP CHUNKS)
# ──────────────────────────────────────────────────────────────
def _download_direct_stream(task_id: str, url: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 5, "speed": "Direct Stream", "eta": ""})
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    
    with urllib.request.urlopen(req, timeout=15) as resp:
        total_size = int(resp.headers.get("Content-Length", 0))
        # Derive filename from URL
        url_path = url.split("?")[0].split("#")[0]
        base_name = os.path.basename(url_path) or f"download_{uuid.uuid4().hex[:6]}.mp4"
        out_path = DOWNLOAD_DIR / _clean_filename(base_name)

        downloaded = 0
        chunk_size = 64 * 1024
        with open(out_path, "wb") as f:
            while True:
                chunk = resp.read(chunk_size)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                pct = int(downloaded / total_size * 100) if total_size else 50
                downloads[task_id].update({
                    "progress": pct,
                    "downloaded_bytes": downloaded,
                    "total_bytes": total_size,
                    "downloaded_str": _format_size(downloaded),
                    "total_str": _format_size(total_size) if total_size else "Unknown",
                    "speed": "Direct",
                })

    _finish_task(task_id, {
        "filename": out_path.name,
        "file_path": str(out_path),
        "size_str": _format_size(out_path.stat().st_size),
        "is_audio": out_path.suffix.lower() in [".mp3", ".wav", ".m4a", ".aac"],
        "title": out_path.name,
        "thumbnail": "",
    })


# ──────────────────────────────────────────────────────────────
# ENGINE 5: YOUTUBE COMMUNITY POST ENGINE
# ──────────────────────────────────────────────────────────────
def _extract_youtube_post_id(url: str) -> Optional[str]:
    m = re.search(r"youtube\.com/post/(Ug[a-zA-Z0-9_\-]+)", url)
    if m:
        return m.group(1)
    m = re.search(r"[?&]lb=(Ug[a-zA-Z0-9_\-]+)", url)
    if m:
        return m.group(1)
    return None


def _fetch_youtube_post(post_id: str) -> Optional[dict]:
    try:
        url = f"https://www.youtube.com/post/{post_id}"
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
                "Accept-Language": "en-US,en;q=0.9",
            },
        )
        with urllib.request.urlopen(req, timeout=12) as resp:
            html = resp.read().decode("utf-8", errors="ignore")

        m = re.search(r"var ytInitialData\s*=\s*({.+?});</script>", html)
        if not m:
            return None
        data = json.loads(m.group(1))

        def find_posts(obj):
            res = []
            if isinstance(obj, dict):
                if "backstagePostRenderer" in obj:
                    res.append(obj["backstagePostRenderer"])
                for v in obj.values():
                    res.extend(find_posts(v))
            elif isinstance(obj, list):
                for item in obj:
                    res.extend(find_posts(item))
            return res

        posts = find_posts(data)
        if not posts:
            return None

        post = posts[0]
        author = post.get("authorText", {}).get("runs", [{}])[0].get("text", "YouTube Creator")
        text_runs = post.get("contentText", {}).get("runs", [])
        text_content = "".join(r.get("text", "") for r in text_runs).strip()
        title = text_content[:80].strip() if text_content else f"Community Post by {author}"

        image_urls = []
        att = post.get("backstageAttachment", {})
        if "backstageImageRenderer" in att:
            thumbs = att["backstageImageRenderer"].get("image", {}).get("thumbnails", [])
            if thumbs:
                raw_url = thumbs[-1].get("url", "")
                image_urls.append(re.sub(r"=s\d+.*", "=s0", raw_url))
        elif "postMultiImageRenderer" in att:
            for img in att["postMultiImageRenderer"].get("images", []):
                thumbs = img.get("backstageImageRenderer", {}).get("image", {}).get("thumbnails", [])
                if thumbs:
                    raw_url = thumbs[-1].get("url", "")
                    image_urls.append(re.sub(r"=s\d+.*", "=s0", raw_url))

        author_thumb = post.get("authorThumbnail", {}).get("thumbnails", [{}])[-1].get("url", "")
        return {
            "post_id": post_id,
            "author": author,
            "title": title,
            "text": text_content,
            "images": image_urls,
            "image_count": len(image_urls),
            "thumbnail": image_urls[0] if image_urls else author_thumb,
        }
    except Exception:
        return None


def _download_youtube_post(task_id: str, post_id: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 25, "speed": "YouTube Post", "eta": "Fetching HD Media"})
    data = _fetch_youtube_post(post_id)
    if not data:
        raise Exception("Could not find YouTube community post or media")

    author = _clean_filename(data["author"]) or "YouTube"
    images = data["images"]
    title = data["title"]

    if not images:
        txt_path = DOWNLOAD_DIR / f"YouTube_{author}_{post_id}_Post.txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(f"YouTube Community Post by {data['author']}\n")
            f.write(f"Post ID: {post_id}\n\n")
            f.write(data["text"])
        _finish_task(task_id, {
            "filename": txt_path.name,
            "file_path": str(txt_path),
            "size_str": _format_size(txt_path.stat().st_size),
            "is_audio": False,
            "title": title,
            "thumbnail": data.get("thumbnail", ""),
        })
        return

    if len(images) == 1:
        img_url = images[0]
        ext = "jpg"
        out_path = DOWNLOAD_DIR / f"YouTube_{author}_{post_id}_Post.{ext}"
        req = urllib.request.Request(img_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            content_type = resp.headers.get("Content-Type", "")
            if "png" in content_type:
                out_path = out_path.with_suffix(".png")
            elif "gif" in content_type:
                out_path = out_path.with_suffix(".gif")
            with open(out_path, "wb") as f:
                f.write(resp.read())

        _finish_task(task_id, {
            "filename": out_path.name,
            "file_path": str(out_path),
            "size_str": _format_size(out_path.stat().st_size),
            "is_audio": False,
            "title": title,
            "thumbnail": img_url,
        })
        return

    # Multi-image carousel
    downloads[task_id].update({"status": "downloading", "progress": 40, "speed": "Packaging ZIP"})
    zip_path = DOWNLOAD_DIR / f"YouTube_{author}_{post_id}_Post.zip"
    saved_items = []

    for idx, img_url in enumerate(images, 1):
        try:
            req = urllib.request.Request(img_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                content_type = resp.headers.get("Content-Type", "")
                ext = "png" if "png" in content_type else ("gif" if "gif" in content_type else "jpg")
                saved_items.append((f"Post_Image_{idx:02d}.{ext}", resp.read()))
        except Exception:
            pass

    if not saved_items:
        raise Exception("Failed to download community post images")

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        if data["text"]:
            z.writestr("Post_Caption.txt", f"YouTube Community Post by {data['author']}\n\n{data['text']}")
        for fname, fbytes in saved_items:
            z.writestr(fname, fbytes)

    _finish_task(task_id, {
        "filename": zip_path.name,
        "file_path": str(zip_path),
        "size_str": _format_size(zip_path.stat().st_size),
        "is_audio": False,
        "title": f"Community Post by {author} ({len(saved_items)} Photos)",
        "thumbnail": images[0],
    })


# ──────────────────────────────────────────────────────────────
# ENGINE 6: YOUTUBE CHANNEL BRANDING (AVATAR & BANNER)
# ──────────────────────────────────────────────────────────────
def _extract_youtube_channel_handle(url: str) -> Optional[str]:
    if any(k in url for k in ["/watch", "/shorts", "/post/", "lb="]):
        return None
    m = re.search(r"youtube\.com/(@[a-zA-Z0-9_\-\.]+)", url)
    if m:
        return m.group(1)
    m = re.search(r"youtube\.com/channel/(UC[a-zA-Z0-9_\-]+)", url)
    if m:
        return m.group(1)
    return None


def _fetch_youtube_channel(handle: str) -> Optional[dict]:
    try:
        url = f"https://www.youtube.com/{handle}" if handle.startswith("@") else f"https://www.youtube.com/channel/{handle}"
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
                "Accept-Language": "en-US,en;q=0.9",
            },
        )
        with urllib.request.urlopen(req, timeout=12) as resp:
            html = resp.read().decode("utf-8", errors="ignore")

        title_m = re.search(r'<meta property="og:title" content="([^"]+)"', html)
        title = title_m.group(1) if title_m else handle

        avatar_m = re.search(r'<meta property="og:image" content="([^"]+)"', html)
        avatar_raw = avatar_m.group(1) if avatar_m else ""
        avatar_hd = re.sub(r"=s\d+.*", "=s0", avatar_raw) if avatar_raw else ""

        banner_raw = ""
        banner_m = re.search(r'https://yt3\.googleusercontent\.com/[^\s"\'\\]+=w\d+-[^"\'\\]+', html)
        if banner_m:
            banner_raw = banner_m.group(0)
        else:
            banner_m2 = re.search(r'"banner":\s*\{"imageBannerViewModel":\s*\{"image":\s*\{"sources":\s*\[\{"url":\s*"([^"]+)"', html)
            if banner_m2:
                banner_raw = banner_m2.group(1)

        banner_hd = re.sub(r"=w\d+.*", "=s0", banner_raw) if banner_raw else ""

        return {
            "handle": handle,
            "title": title,
            "avatar_hd": avatar_hd,
            "banner_hd": banner_hd,
            "thumbnail": avatar_hd or banner_hd,
        }
    except Exception:
        return None


def _download_youtube_channel(task_id: str, handle: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 25, "speed": "Channel Art", "eta": "Fetching Ultra HD"})
    data = _fetch_youtube_channel(handle)
    if not data:
        raise Exception(f"Could not load channel branding for {handle}")

    safe_title = _clean_filename(data["title"]) or "YouTube_Channel"
    avatar_url = data["avatar_hd"]
    banner_url = data["banner_hd"]

    if fmt == "banner" and banner_url:
        out_path = DOWNLOAD_DIR / f"YouTube_{safe_title}_Banner_HD.jpg"
        req = urllib.request.Request(banner_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp, open(out_path, "wb") as f:
            f.write(resp.read())
        _finish_task(task_id, {
            "filename": out_path.name,
            "file_path": str(out_path),
            "size_str": _format_size(out_path.stat().st_size),
            "is_audio": False,
            "title": f"{data['title']} Banner (2560x1440 HD)",
            "thumbnail": banner_url,
        })
        return

    if fmt in ["avatar", "thumbnail"] and avatar_url:
        out_path = DOWNLOAD_DIR / f"YouTube_{safe_title}_Avatar_HD.jpg"
        req = urllib.request.Request(avatar_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp, open(out_path, "wb") as f:
            f.write(resp.read())
        _finish_task(task_id, {
            "filename": out_path.name,
            "file_path": str(out_path),
            "size_str": _format_size(out_path.stat().st_size),
            "is_audio": False,
            "title": f"{data['title']} Avatar (DP HD)",
            "thumbnail": avatar_url,
        })
        return

    # fmt == "best" -> Both in a ZIP bundle
    if avatar_url and banner_url:
        zip_path = DOWNLOAD_DIR / f"YouTube_{safe_title}_Branding_HD.zip"
        req_av = urllib.request.Request(avatar_url, headers={"User-Agent": "Mozilla/5.0"})
        req_bn = urllib.request.Request(banner_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req_av, timeout=15) as r1, urllib.request.urlopen(req_bn, timeout=15) as r2:
            av_bytes = r1.read()
            bn_bytes = r2.read()
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr(f"{safe_title}_Avatar_HD.jpg", av_bytes)
            z.writestr(f"{safe_title}_Banner_2560x1440_HD.jpg", bn_bytes)
        _finish_task(task_id, {
            "filename": zip_path.name,
            "file_path": str(zip_path),
            "size_str": _format_size(zip_path.stat().st_size),
            "is_audio": False,
            "title": f"{data['title']} Channel Art (Avatar + Banner)",
            "thumbnail": avatar_url,
        })
    elif avatar_url:
        out_path = DOWNLOAD_DIR / f"YouTube_{safe_title}_Avatar_HD.jpg"
        req = urllib.request.Request(avatar_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp, open(out_path, "wb") as f:
            f.write(resp.read())
        _finish_task(task_id, {
            "filename": out_path.name,
            "file_path": str(out_path),
            "size_str": _format_size(out_path.stat().st_size),
            "is_audio": False,
            "title": f"{data['title']} Avatar (DP HD)",
            "thumbnail": avatar_url,
        })
    else:
        raise Exception("No channel avatar or banner available")


# ──────────────────────────────────────────────────────────────
# ENGINE 7: INSTAGRAM PROFILE DP ENGINE
# ──────────────────────────────────────────────────────────────
def _extract_instagram_username(url: str) -> Optional[str]:
    url = url.strip()
    if url.startswith("@"):
        return url.lstrip("@").strip()
    m = re.search(r"(?:https?://)?(?:www\.)?instagram\.com/([a-zA-Z0-9_\.]+)/?", url)
    if m:
        cand = m.group(1).lower()
        if cand not in ["p", "reel", "reels", "stories", "tv", "explore", "direct", "accounts"]:
            return cand
    return None


def _fetch_instagram_profile(username: str) -> Optional[dict]:
    try:
        profile = instaloader.Profile.from_username(_insta_loader.context, username)
        hd_pic = profile._node.get("profile_pic_url_hd") or profile.profile_pic_url
        followers_cnt = profile.followers
        f_str = f"{followers_cnt / 1_000_000:.1f}M" if followers_cnt >= 1_000_000 else (f"{followers_cnt / 1_000:.1f}K" if followers_cnt >= 1_000 else str(followers_cnt))
        verified_str = "Verified Profile" if profile.is_verified else "Instagram Profile"
        return {
            "username": profile.username,
            "full_name": profile.full_name or profile.username,
            "hd_pic": hd_pic,
            "followers_str": f_str,
            "verified_str": verified_str,
            "bio": profile.biography or "",
        }
    except Exception:
        return None


def _download_instagram_dp(task_id: str, username: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 25, "speed": "Instagram DP", "eta": "Fetching HD Avatar"})
    data = _fetch_instagram_profile(username)
    if not data or not data.get("hd_pic"):
        raise Exception(f"Could not load Instagram profile picture for @{username}")

    hd_url = data["hd_pic"]
    out_path = DOWNLOAD_DIR / f"Instagram_{username}_DP.jpg"
    req = urllib.request.Request(hd_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=15) as resp, open(out_path, "wb") as f:
        f.write(resp.read())

    _finish_task(task_id, {
        "filename": out_path.name,
        "file_path": str(out_path),
        "size_str": _format_size(out_path.stat().st_size),
        "is_audio": False,
        "title": f"{data['full_name']} HD DP (@{username})",
        "thumbnail": hd_url,
    })


# ──────────────────────────────────────────────────────────────
# ENGINE 8: TWITTER / X PROFILE DP ENGINE
# ──────────────────────────────────────────────────────────────
def _extract_twitter_username(url: str) -> Optional[str]:
    url = url.strip()
    m = re.search(r"(?:https?://)?(?:www\.)?(?:twitter|x)\.com/([a-zA-Z0-9_]+)/?", url)
    if m:
        cand = m.group(1).lower()
        if cand not in ["home", "explore", "notifications", "messages", "search", "i", "settings"]:
            return cand
    return None


def _fetch_twitter_profile(username: str) -> Optional[dict]:
    try:
        url = f"https://x.com/{username}"
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
                "Accept-Language": "en-US,en;q=0.9",
            },
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            html = resp.read().decode("utf-8", errors="ignore")

        m = re.findall(r'https://pbs\.twimg\.com/profile_images/[^\s"\'\\]+', html)
        if not m:
            return None
        pic_hd = re.sub(r"_(?:normal|200x200|mini|reasonably_small)\.", ".", m[0])
        return {
            "username": username,
            "name": username,
            "pic_hd": pic_hd,
        }
    except Exception:
        return None


def _download_twitter_dp(task_id: str, username: str, fmt: str):
    downloads[task_id].update({"status": "downloading", "progress": 25, "speed": "Twitter DP", "eta": "Fetching HD Avatar"})
    data = _fetch_twitter_profile(username)
    if not data or not data.get("pic_hd"):
        raise Exception(f"Could not load Twitter / X profile picture for @{username}")

    pic_url = data["pic_hd"]
    ext = "png" if ".png" in pic_url else "jpg"
    out_path = DOWNLOAD_DIR / f"Twitter_{username}_DP.{ext}"
    req = urllib.request.Request(pic_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=15) as resp, open(out_path, "wb") as f:
        f.write(resp.read())

    _finish_task(task_id, {
        "filename": out_path.name,
        "file_path": str(out_path),
        "size_str": _format_size(out_path.stat().st_size),
        "is_audio": False,
        "title": f"@{username} Profile Picture (HD DP)",
        "thumbnail": pic_url,
    })


# ──────────────────────────────────────────────────────────────
# ENGINE 4: YT-DLP CORE ENGINE (YOUTUBE, SHORTS, TWITTER, TIKTOK)
# ──────────────────────────────────────────────────────────────
def _base_ydl_opts(outtmpl: str, hooks: list) -> dict:
    js_runtimes = {"node": {"path": _NODE_PATH}} if _NODE_PATH else {}
    return {
        "outtmpl": outtmpl,
        "progress_hooks": hooks,
        "quiet": True,
        "no_warnings": True,
        "ignoreerrors": False,
        "merge_output_format": "mp4",
        "js_runtimes": js_runtimes,
        "remote_components": {"ejs:github"},
        "extractor_args": {
            "youtube": {
                "player_client": ["ios", "android", "mweb", "web"]
            }
        },
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Accept": "*/*",
            "Accept-Language": "en-US,en;q=0.9",
            "Sec-Fetch-Mode": "navigate",
        },
        # High-Speed Multi-Threaded Fragment & Chunk Acceleration
        "concurrent_fragment_downloads": 6,  # 6 parallel fragments (DASH/HLS line-rate saturation)
        "http_chunk_size": 10485760,         # 10MB chunk range requests (prevents YouTube throttling dips)
        "buffersize": 1024 * 1024,           # 1MB socket buffer (prevents TCP pipeline stalls)
        "throttled_rate": 100 * 1024,        # Auto-drop and reconnect if YouTube CDN throttles below 100KB/s
        "retries": 15,
        "fragment_retries": 15,
        "socket_timeout": 20,
    }


def _info_ydl_opts() -> dict:
    js_runtimes = {"node": {"path": _NODE_PATH}} if _NODE_PATH else {}
    return {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "js_runtimes": js_runtimes,
        "remote_components": {"ejs:github"},
        "extractor_args": {
            "youtube": {
                "player_client": ["ios", "android", "mweb", "web"]
            }
        },
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Accept": "*/*",
            "Accept-Language": "en-US,en;q=0.9",
        },
        "socket_timeout": 8,
        "retries": 2,
    }


def _make_format_opts(fmt: str) -> dict:
    if fmt == "mp3":
        return {
            "format": "bestaudio/best",
            "postprocessors": [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "320",
            }],
        }
    if fmt in ["8k", "4320p"]:
        return {
            "format": "bestvideo[height<=4320]+bestaudio/best[height<=4320]/best",
        }
    if fmt in ["4k", "2160p"]:
        return {
            "format": "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best",
        }
    if fmt in ["2k", "1440p"]:
        return {
            "format": "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best",
        }
    if fmt == "1080p":
        return {
            "format": "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
        }
    if fmt == "720p":
        return {
            "format": "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
        }
    if fmt == "480p":
        return {
            "format": "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
        }
    # "best" - maximum quality available (8K 4320p / 4K 2160p / 2K 1440p / 1080p FHD)
    return {
        "format": "bestvideo+bestaudio/best",
    }


def _download_ytdlp(task_id: str, url: str, fmt: str):
    if fmt == "thumbnail":
        with yt_dlp.YoutubeDL(_info_ydl_opts()) as ydl:
            info = ydl.extract_info(url, download=False)
            if "entries" in info:
                info = info["entries"][0]
            thumb_url = info.get("thumbnail") or ""
            title = info.get("title") or "cover"

        if not thumb_url:
            raise Exception("No high resolution cover found")

        safe_name = _clean_filename(title)
        img_path = DOWNLOAD_DIR / f"{safe_name}_Cover.jpg"
        req = urllib.request.Request(thumb_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp, open(img_path, "wb") as f:
            f.write(resp.read())

        _finish_task(task_id, {
            "filename": img_path.name,
            "file_path": str(img_path),
            "size_str": _format_size(img_path.stat().st_size),
            "is_audio": False,
            "title": title,
            "thumbnail": thumb_url,
        })
        return

    outtmpl = str(DOWNLOAD_DIR / "%(title).90s.%(ext)s")

    def progress_hook(d):
        if d["status"] == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes", 0)
            pct = int(downloaded / total * 100) if total else 0

            # Stabilized Speed & ETA Smoothing via Exponential Moving Average (EMA)
            raw_speed = d.get("speed")
            with _lock:
                task_data = downloads.get(task_id, {})
                if task_data.get("status") == "cancelled":
                    raise Exception("Download cancelled by user")
                prev_speed = task_data.get("_smoothed_speed")
                if raw_speed and raw_speed > 0:
                    if prev_speed is None or prev_speed <= 0:
                        smoothed_speed = float(raw_speed)
                    else:
                        # Alpha = 0.28 gives responsive adaptation while filtering wild 1-sample spikes/drops
                        smoothed_speed = 0.28 * float(raw_speed) + 0.72 * prev_speed
                else:
                    smoothed_speed = prev_speed or 0.0

                speed_str = _format_speed(smoothed_speed) if smoothed_speed > 0 else (d.get("_speed_str", "").strip() or "Streaming...")

                # Stabilized ETA countdown
                remaining = max(0, total - downloaded) if total else 0
                if smoothed_speed > 0 and remaining > 0:
                    eta_str = _format_eta(remaining / smoothed_speed)
                else:
                    eta_str = d.get("_eta_str", "").strip()

                downloads[task_id].update({
                    "status": "downloading",
                    "progress": pct,
                    "downloaded_bytes": downloaded,
                    "total_bytes": total,
                    "downloaded_str": _format_size(downloaded),
                    "total_str": _format_size(total) if total else "Unknown",
                    "_smoothed_speed": smoothed_speed,
                    "speed": speed_str,
                    "eta": eta_str,
                })
        elif d["status"] == "finished":
            with _lock:
                if task_id in downloads:
                    downloads[task_id]["status"] = "processing"
                    downloads[task_id]["filename"] = Path(d["filename"]).name

    opts = {
        **_base_ydl_opts(outtmpl, [progress_hook]),
        **_make_format_opts(fmt),
    }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if "entries" in info:
                info = info["entries"][0]

            filename = ydl.prepare_filename(info)
            if fmt == "mp3":
                filename = str(Path(filename).with_suffix(".mp3"))
            elif fmt != "thumbnail":
                cand_mp4 = Path(filename).with_suffix(".mp4")
                if cand_mp4.exists():
                    filename = str(cand_mp4)

            file_path = Path(filename)
            if not file_path.exists():
                stem_sub = re.sub(r"[^\w\s-]", "", file_path.stem[:25]).strip().lower()
                candidates = []
                for item in DOWNLOAD_DIR.iterdir():
                    if item.is_file() and not item.name.endswith(".part") and not item.name.endswith(".ytdl"):
                        if stem_sub and stem_sub in item.name.lower():
                            candidates.append(item)
                        elif item.stat().st_mtime >= (time.time() - 300):
                            candidates.append(item)
                if candidates:
                    candidates.sort(key=lambda x: x.stat().st_mtime, reverse=True)
                    file_path = candidates[0]

            actual_size = file_path.stat().st_size if file_path.exists() else 0

            _finish_task(task_id, {
                "filename": file_path.name,
                "file_path": str(file_path.resolve()),
                "size_str": _format_size(actual_size),
                "is_audio": fmt == "mp3",
                "title": info.get("title", file_path.name),
                "thumbnail": info.get("thumbnail", ""),
            })
    except Exception as e:
        if "No video formats found" in str(e) and "instagram.com" in url.lower():
            # Automatically redirect to Instagram specialist engine
            _download_instagram(task_id, url, fmt)
            return
        raise e


def _download_trimmed_media(task_id: str, url: str, fmt: str, start_time: str, end_time: str):
    """Zero-full-download precision trimmer using FFmpeg input seeking."""
    with _lock:
        if task_id in downloads:
            downloads[task_id].update({
                "status": "downloading",
                "progress": 20,
                "speed": "Virtual Byte-Range Seek",
                "eta": f"Clipping {start_time} - {end_time}",
            })

    with yt_dlp.YoutubeDL(_info_ydl_opts()) as ydl:
        info = ydl.extract_info(url, download=False)
        if "entries" in info:
            info = info["entries"][0]
        title = info.get("title") or "clip"
        formats = info.get("formats") or []

        direct_url = None
        if fmt == "mp3":
            audio_formats = [f for f in formats if f.get("vcodec") == "none" and f.get("url")]
            if audio_formats:
                audio_formats.sort(key=lambda x: x.get("abr") or 0, reverse=True)
                direct_url = audio_formats[0]["url"]
        else:
            prog = [f for f in formats if f.get("vcodec") != "none" and f.get("acodec") != "none" and f.get("url")]
            if prog:
                prog.sort(key=lambda x: x.get("height") or 0, reverse=True)
                direct_url = prog[0]["url"]
            elif formats:
                direct_url = formats[-1]["url"]

    if not direct_url:
        _download_ytdlp(task_id, url, fmt)
        return

    safe_title = _clean_filename(title)
    s_clean = start_time.replace(":", ".")
    e_clean = end_time.replace(":", ".")
    ext = "mp3" if fmt == "mp3" else "mp4"
    out_file = DOWNLOAD_DIR / f"{safe_title}_Clip_{s_clean}_to_{e_clean}.{ext}"

    with _lock:
        if task_id in downloads:
            downloads[task_id].update({"status": "processing", "progress": 60, "speed": "FFmpeg Range Cut", "eta": "Finalizing"})

    cmd = [_FFMPEG_PATH, "-y", "-ss", start_time, "-to", end_time, "-i", direct_url]
    if fmt == "mp3":
        cmd += ["-vn", "-c:a", "libmp3lame", "-b:a", "320k", str(out_file.resolve())]
    else:
        cmd += ["-c", "copy", "-movflags", "+faststart", str(out_file.resolve())]

    subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if not out_file.exists() or out_file.stat().st_size == 0:
        # Keyframe boundary fallback
        cmd_reencode = [_FFMPEG_PATH, "-y", "-ss", start_time, "-to", end_time, "-i", direct_url, "-c:v", "libx264", "-c:a", "aac", str(out_file.resolve())]
        subprocess.run(cmd_reencode, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    if not out_file.exists():
        raise Exception("FFmpeg trimmer was unable to render the selected clip range")

    _finish_task(task_id, {
        "filename": out_file.name,
        "file_path": str(out_file.resolve()),
        "size_str": _format_size(out_file.stat().st_size),
        "is_audio": fmt == "mp3",
        "title": f"{title} (Clip {start_time}-{end_time})",
        "thumbnail": info.get("thumbnail") or "",
    })


# ──────────────────────────────────────────────────────────────
# MASTER ROUTER DISPATCHER
# ──────────────────────────────────────────────────────────────
def master_download_worker(task_id: str, url: str, fmt: str, start_time: Optional[str] = None, end_time: Optional[str] = None):
    with _lock:
        if task_id in downloads and downloads[task_id].get("status") == "cancelled":
            return
    try:
        if start_time and end_time:
            _download_trimmed_media(task_id, url, fmt, start_time, end_time)
            return

        url_lower = url.lower()

        # 1. YouTube Community Post
        yt_post_id = _extract_youtube_post_id(url)
        if yt_post_id:
            _download_youtube_post(task_id, yt_post_id, fmt)
            return

        # 2. YouTube Channel Art & Avatar
        yt_channel_handle = _extract_youtube_channel_handle(url)
        if yt_channel_handle:
            _download_youtube_channel(task_id, yt_channel_handle, fmt)
            return

        # 3. Instagram DP / Profile (exclude posts / reels)
        ig_user = _extract_instagram_username(url)
        if ig_user and not _get_instagram_shortcode(url):
            _download_instagram_dp(task_id, ig_user, fmt)
            return

        # 4. Twitter / X Profile DP (exclude status / tweet)
        tw_user = _extract_twitter_username(url)
        if tw_user and "/status/" not in url_lower:
            _download_twitter_dp(task_id, tw_user, fmt)
            return

        # 5. Instagram Media (Reels, Posts, Carousels)
        if any(d in url_lower for d in ["instagram.com", "instagr.am", "ig.me"]):
            _download_instagram(task_id, url, fmt)
        elif "spotify.com" in url_lower:
            _download_spotify(task_id, url, fmt)
        elif re.search(r"\.(mp4|mkv|webm|mp3|wav|jpg|png|zip|pdf)($|\?)", url_lower) and not any(k in url_lower for k in ["youtube", "youtu.be", "twitter", "x.com"]):
            try:
                _download_direct_stream(task_id, url, fmt)
            except Exception:
                _download_ytdlp(task_id, url, fmt)
        else:
            _download_ytdlp(task_id, url, fmt)
    except Exception as e:
        with _lock:
            if task_id in downloads and downloads[task_id].get("status") == "cancelled":
                return
            err = str(e)
            for prefix in ["ERROR: ", "[youtube] ", "[generic] ", "[Instagram] "]:
                err = err.replace(prefix, "")
            # Remove noisy GitHub issue template instructions
            if "; please report this issue" in err:
                err = err.split("; please report this issue")[0].strip()
            if task_id in downloads:
                downloads[task_id].update({"status": "error", "error": err[:350]})


# ──────────────────────────────────────────────────────────────
# REST Endpoints
# ──────────────────────────────────────────────────────────────
@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "Downloadivo",
        "version": "5.1.0",
        "timestamp": datetime.now().isoformat(),
    }


@app.get("/", response_class=HTMLResponse)
def index():
    html_file = Path(__file__).parent / "index.html"
    with open(html_file, encoding="utf-8") as f:
        return f.read()


@app.get("/system-info")
def system_info(request: Request):
    is_remote = _is_remote_request(request)
    is_localhost = not is_remote
    client_ip = (
        request.headers.get("cf-connecting-ip")
        or (request.headers.get("x-forwarded-for", "").split(",")[0].strip())
        or (request.client.host if request.client else "127.0.0.1")
    )
    total, used, free = shutil.disk_usage(DOWNLOAD_DIR)
    files = [f for f in DOWNLOAD_DIR.glob("*") if f.is_file() and not f.name.endswith(".gitkeep")]
    consumed = sum(f.stat().st_size for f in files)
    public_url = _get_public_tunnel_url()

    return {
        "download_dir": str(DOWNLOAD_DIR),
        "disk_free": _format_size(free),
        "disk_total": _format_size(total),
        "downloads_count": len(files),
        "downloads_size": _format_size(consumed),
        "node_available": bool(_NODE_PATH),
        "ffmpeg_available": bool(_FFMPEG_PATH),
        "ytdlp_version": yt_dlp.version.__version__,
        "is_localhost": is_localhost,
        "is_remote": is_remote,
        "is_cloud": not hasattr(os, "startfile"),
        "client_ip": client_ip,
        "public_url": public_url,
    }


@app.post("/info")
def get_info(req: InfoRequest, request: Request):
    client_ip = (
        request.headers.get("cf-connecting-ip")
        or (request.headers.get("x-forwarded-for", "").split(",")[0].strip())
        or (request.client.host if request.client else "127.0.0.1")
    )
    if not _check_rate_limit(client_ip):
        raise HTTPException(status_code=429, detail="Too many preview requests. Please wait a moment.")

    url = req.url
    url_lower = url.lower()

    # 0. YouTube Community Post
    yt_post_id = _extract_youtube_post_id(url)
    if yt_post_id:
        post_data = _fetch_youtube_post(yt_post_id)
        if post_data:
            img_cnt = post_data.get("image_count", 0)
            author = post_data.get("author", "YouTube Creator")
            duration_str = f"{img_cnt} Photos (Post)" if img_cnt > 1 else ("1 Photo (Post)" if img_cnt == 1 else "Text Post")
            res_list = [f"Original HD ({img_cnt} Photos + ZIP)"] if img_cnt > 1 else ["Original HD (Post Image)"]
            return {
                "title": post_data.get("title") or f"Community Post by {author}",
                "thumbnail": post_data.get("thumbnail") or "",
                "duration": duration_str,
                "uploader": f"{author} (YouTube Post)",
                "extractor": "YouTube Community Post",
                "is_youtube": True,
                "is_8k": False,
                "is_4k": False,
                "is_post": True,
                "resolutions": res_list,
            }

    # 0.1 YouTube Channel Branding (Avatar & Banner)
    yt_channel_handle = _extract_youtube_channel_handle(url)
    if yt_channel_handle:
        ch_data = _fetch_youtube_channel(yt_channel_handle)
        if ch_data:
            return {
                "title": f"{ch_data['title']} (YouTube Channel)",
                "thumbnail": ch_data["avatar_hd"] or ch_data["banner_hd"],
                "duration": "Channel Branding",
                "uploader": ch_data["handle"],
                "extractor": "YouTube Channel Art",
                "is_youtube": True,
                "is_8k": False,
                "is_4k": False,
                "is_channel": True,
                "resolutions": ["Channel Avatar (DP HD)", "Channel Banner (2560x1440 HD)", "Both (Avatar + Banner ZIP)"],
            }

    # 0.2 Instagram Profile DP
    ig_user = _extract_instagram_username(url)
    if ig_user and not _get_instagram_shortcode(url):
        dp_data = _fetch_instagram_profile(ig_user)
        if dp_data:
            return {
                "title": f"{dp_data['full_name']} (@{dp_data['username']})",
                "thumbnail": dp_data["hd_pic"],
                "duration": f"{dp_data['followers_str']} Followers",
                "uploader": f"@{dp_data['username']} ({dp_data['verified_str']})",
                "extractor": "Instagram Profile DP",
                "is_youtube": False,
                "is_8k": False,
                "is_4k": False,
                "is_profile": True,
                "resolutions": ["Original HD (Profile Picture DP)"],
            }

    # 0.3 Twitter / X Profile DP
    tw_user = _extract_twitter_username(url)
    if tw_user and "/status/" not in url_lower:
        tw_data = _fetch_twitter_profile(tw_user)
        if tw_data:
            return {
                "title": f"@{tw_data['username']} (Twitter / X Profile)",
                "thumbnail": tw_data["pic_hd"],
                "duration": "Twitter / X Profile",
                "uploader": f"@{tw_data['username']}",
                "extractor": "Twitter / X DP",
                "is_youtube": False,
                "is_8k": False,
                "is_4k": False,
                "is_profile": True,
                "resolutions": ["Original HD (Profile Picture DP)"],
            }

    # 1. Instagram fast inspector
    if "instagram.com" in url_lower:
        # First try InstagramIE
        try:
            ydl = yt_dlp.YoutubeDL({'quiet': True, 'skip_download': True, 'no_warnings': True})
            ie = yt_dlp.extractor.instagram.InstagramIE(ydl)
            info = ie.extract(url)
            entries = info.get("entries") or [info]
            count = len(entries)
            is_carousel = count > 1
            has_video = any(len(e.get("formats", [])) > 0 for e in entries)
            author = info.get("uploader") or info.get("channel") or ""
            uploader_str = f"@{author}" if author else "Instagram"

            title = info.get("title") or ""
            if not title or title.startswith("Video by") or title.startswith("Post by"):
                if info.get("description"):
                    title = info["description"][:80].strip()
                else:
                    title = f"Post by {uploader_str}"
            if is_carousel:
                title = f"{title} ({count} items)"

            first_entry = entries[0]
            thumbs = first_entry.get("thumbnails", [])
            thumb = thumbs[-1]["url"] if thumbs else (info.get("thumbnail") or "")

            duration_str = f"{count} items" if is_carousel else ("Reel" if has_video else "Photo")
            resolutions = ["1080p Full HD"] if has_video else [f"Original HD ({count} Photos)"]

            return {
                "title": title,
                "thumbnail": thumb,
                "duration": duration_str,
                "uploader": uploader_str,
                "extractor": "Instagram",
                "is_youtube": False,
                "is_8k": False,
                "is_4k": False,
                "is_carousel": is_carousel,
                "item_count": count,
                "has_video": has_video,
                "resolutions": resolutions,
            }
        except Exception:
            pass

        # Second try Instaloader fallback
        shortcode = _get_instagram_shortcode(url)
        if shortcode:
            try:
                post = instaloader.Post.from_shortcode(_insta_loader.context, shortcode)
                owner = post.owner_username or "user"
                title = post.caption[:80] if post.caption else f"Instagram Post by @{owner}"
                return {
                    "title": title,
                    "thumbnail": post.url,
                    "duration": "Reel" if post.is_video else f"Photo ({post.mediacount} items)",
                    "uploader": f"@{owner}",
                    "extractor": "Instagram",
                    "is_youtube": False,
                    "is_8k": False,
                    "is_4k": False,
                    "is_carousel": post.mediacount > 1,
                    "item_count": post.mediacount,
                    "has_video": post.is_video,
                    "resolutions": ["1080p HD"],
                }
            except Exception:
                pass

    # 2. Spotify fast inspector
    if "spotify.com" in url_lower:
        try:
            req_sp = urllib.request.Request(f"https://open.spotify.com/oembed?url={url}", headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req_sp, timeout=4) as resp:
                sdata = json.loads(resp.read().decode())
                return {
                    "title": sdata.get("title") or "Spotify Song",
                    "thumbnail": sdata.get("thumbnail_url") or "",
                    "duration": "Track",
                    "uploader": sdata.get("author_name") or "Spotify",
                    "extractor": "Spotify",
                    "resolutions": [],
                }
        except Exception:
            pass

    # 3. Standard yt-dlp inspector
    try:
        with yt_dlp.YoutubeDL(_info_ydl_opts()) as ydl:
            info = ydl.extract_info(url, download=False)
            if "entries" in info:
                info = info["entries"][0]
            
            formats = info.get("formats") or []
            heights = set()
            for f in formats:
                h = f.get("height")
                if h and isinstance(h, int) and h >= 360:
                    heights.add(h)
            
            sorted_resolutions = sorted(list(heights), reverse=True)
            extractor = (info.get("extractor_key") or "Web").capitalize()
            is_youtube = "youtube" in url_lower or "youtu.be" in url_lower
            is_8k = 4320 in heights or any((f.get("height") or 0) >= 4320 for f in formats)
            is_4k = 2160 in heights or any((f.get("height") or 0) >= 2160 for f in formats)

            return {
                "title": info.get("title", "Untitled Media"),
                "thumbnail": info.get("thumbnail", ""),
                "duration": info.get("duration_string") or str(info.get("duration") or ""),
                "uploader": info.get("uploader") or info.get("channel") or "Unknown Author",
                "extractor": extractor,
                "is_youtube": is_youtube,
                "is_8k": is_8k,
                "is_4k": is_4k,
                "resolutions": sorted_resolutions,
            }
    except Exception as e:
        err = str(e).replace("ERROR: ", "").replace("[youtube] ", "")
        raise HTTPException(status_code=400, detail=err[:250])


@app.post("/download")
async def start_download(req: DownloadRequest, request: Request):
    client_ip = (
        request.headers.get("cf-connecting-ip")
        or (request.headers.get("x-forwarded-for", "").split(",")[0].strip())
        or (request.client.host if request.client else "127.0.0.1")
    )
    if not _check_rate_limit(client_ip):
        raise HTTPException(status_code=429, detail="Too many download requests. Please wait a moment.")

    extracted = _extract_urls(req.url)
    target_url = extracted[0] if extracted else req.url.strip()
    if not target_url:
        raise HTTPException(status_code=400, detail="Please provide a valid media URL")
    task_id = _create_task(target_url, req.format)
    _executor.submit(_run_task_worker, task_id, target_url, req.format, req.start_time, req.end_time)
    return {"task_id": task_id}


@app.get("/stream")
def stream_media(url: str, format: str = "best", request: Request = None):
    """Zero-disk high-speed chunked stream proxy directly into client Chrome browser."""
    if request:
        client_ip = (
            request.headers.get("cf-connecting-ip")
            or (request.headers.get("x-forwarded-for", "").split(",")[0].strip())
            or (request.client.host if request.client else "127.0.0.1")
        )
        if not _check_rate_limit(client_ip):
            raise HTTPException(status_code=429, detail="Rate limit exceeded.")

    extracted = _extract_urls(url)
    target_url = extracted[0] if extracted else url.strip()

    try:
        with yt_dlp.YoutubeDL(_info_ydl_opts()) as ydl:
            info = ydl.extract_info(target_url, download=False)
            if "entries" in info:
                info = info["entries"][0]
            title = info.get("title") or "download"
            formats = info.get("formats") or []
    except Exception as e:
        err = str(e).replace("ERROR: ", "").replace("[youtube] ", "")
        raise HTTPException(status_code=400, detail=f"Cannot resolve media stream: {err[:200]}")

    safe_name = _clean_filename(title)
    fmt_lower = (format or "best").lower()

    # 1. Audio stream requested (MP3 / Audio)
    if fmt_lower in ["mp3", "audio"]:
        audio_fmts = [f for f in formats if f.get("acodec") != "none" and f.get("url")]
        audio_fmts.sort(key=lambda x: x.get("abr") or 0, reverse=True)
        if not audio_fmts:
            raise HTTPException(status_code=400, detail="No audio stream available")
        direct_url = audio_fmts[0]["url"]

        def iter_audio():
            import requests
            with requests.get(direct_url, stream=True, timeout=30, headers={"User-Agent": "Mozilla/5.0"}) as r:
                for chunk in r.iter_content(chunk_size=1024 * 512):
                    if chunk:
                        yield chunk

        return StreamingResponse(
            iter_audio(),
            media_type="audio/mpeg",
            headers={
                "Content-Disposition": f'attachment; filename="{safe_name}.mp3"',
                "Accept-Ranges": "bytes",
            }
        )

    # 2. Video stream requested
    # Fast path: Progressive format (video + audio pre-combined) -> zero server CPU, instant start
    prog = [f for f in formats if f.get("vcodec") != "none" and f.get("acodec") != "none" and f.get("url")]
    chosen_url = None

    if prog:
        if fmt_lower == "720p":
            matches = [f for f in prog if (f.get("height") or 0) <= 720]
            chosen_url = matches[-1]["url"] if matches else prog[-1]["url"]
        elif fmt_lower == "480p":
            matches = [f for f in prog if (f.get("height") or 0) <= 480]
            chosen_url = matches[-1]["url"] if matches else prog[-1]["url"]
        elif fmt_lower == "360p":
            matches = [f for f in prog if (f.get("height") or 0) <= 360]
            chosen_url = matches[-1]["url"] if matches else prog[-1]["url"]
        elif fmt_lower in ["best", "original"]:
            chosen_url = prog[-1]["url"]

    if chosen_url:
        def iter_stream():
            import requests
            with requests.get(chosen_url, stream=True, timeout=30, headers={"User-Agent": "Mozilla/5.0"}) as r:
                for chunk in r.iter_content(chunk_size=1024 * 512):
                    if chunk:
                        yield chunk

        return StreamingResponse(
            iter_stream(),
            media_type="video/mp4",
            headers={
                "Content-Disposition": f'attachment; filename="{safe_name}.mp4"',
                "Accept-Ranges": "bytes",
            }
        )

    # 3. Separate video + audio streams available -> Pipe mux via FFmpeg in real time
    video_fmts = [f for f in formats if f.get("vcodec") != "none" and f.get("url")]
    audio_fmts = [f for f in formats if f.get("acodec") != "none" and f.get("url")]

    if video_fmts and audio_fmts and _FFMPEG_PATH:
        if fmt_lower == "1080p":
            matches = [f for f in video_fmts if (f.get("height") or 0) <= 1080]
            chosen_v = matches[-1]["url"] if matches else video_fmts[-1]["url"]
        elif fmt_lower == "4k":
            matches = [f for f in video_fmts if (f.get("height") or 0) <= 2160]
            chosen_v = matches[-1]["url"] if matches else video_fmts[-1]["url"]
        elif fmt_lower == "8k":
            chosen_v = video_fmts[-1]["url"]
        else:
            chosen_v = video_fmts[-1]["url"]

        chosen_a = audio_fmts[-1]["url"]

        def iter_ffmpeg_pipe():
            cmd = [
                _FFMPEG_PATH,
                "-y",
                "-loglevel", "error",
                "-i", chosen_v,
                "-i", chosen_a,
                "-c:v", "copy",
                "-c:a", "aac",
                "-movflags", "frag_keyframe+empty_moov+default_base_moof",
                "-f", "mp4",
                "pipe:1"
            ]
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                while True:
                    data = proc.stdout.read(64 * 1024)
                    if not data:
                        break
                    yield data
            finally:
                if proc.poll() is None:
                    proc.kill()

        return StreamingResponse(
            iter_ffmpeg_pipe(),
            media_type="video/mp4",
            headers={
                "Content-Disposition": f'attachment; filename="{safe_name}.mp4"',
                "Accept-Ranges": "bytes",
            }
        )

    # 4. Fallback direct stream
    fallback_url = formats[-1]["url"] if formats else None
    if not fallback_url:
        raise HTTPException(status_code=400, detail="Could not resolve media stream URL")

    def iter_fallback():
        import requests
        with requests.get(fallback_url, stream=True, timeout=30, headers={"User-Agent": "Mozilla/5.0"}) as r:
            for chunk in r.iter_content(chunk_size=1024 * 512):
                if chunk:
                    yield chunk

    return StreamingResponse(
        iter_fallback(),
        media_type="video/mp4",
        headers={
            "Content-Disposition": f'attachment; filename="{safe_name}.mp4"',
            "Accept-Ranges": "bytes",
        }
    )


@app.get("/status/{task_id}")
async def get_status(task_id: str):
    with _lock:
        if task_id not in downloads:
            raise HTTPException(status_code=404, detail="Task not found")
        data = dict(downloads[task_id])
    if data.get("status") not in ["done", "error", "cancelled"]:
        elapsed = max(0.1, time.time() - data.get("start_ts", time.time()))
        data["elapsed_sec"] = round(elapsed, 1)
        data["elapsed_str"] = _format_duration(elapsed)
    return data


@app.post("/cancel/{task_id}")
async def cancel_task(task_id: str):
    with _lock:
        if task_id in downloads:
            if downloads[task_id]["status"] not in ["done", "error"]:
                downloads[task_id]["status"] = "cancelled"
                downloads[task_id]["error"] = "Cancelled by user"
            return {"status": "cancelled", "task_id": task_id}
    raise HTTPException(status_code=404, detail="Task not found")


@app.get("/downloads")
def list_downloads():
    files_data = []
    try:
        for f in sorted(DOWNLOAD_DIR.iterdir(), key=lambda x: x.stat().st_mtime, reverse=True):
            if f.is_file():
                stat = f.stat()
                ext = f.suffix.lower().replace(".", "")
                is_video = ext in ["mp4", "mkv", "webm", "avi", "mov", "flv"]
                is_audio = ext in ["mp3", "m4a", "flac", "wav", "aac", "opus", "ogg"]
                files_data.append({
                    "name": f.name,
                    "size": stat.st_size,
                    "size_str": _format_size(stat.st_size),
                    "ext": ext.upper(),
                    "is_video": is_video,
                    "is_audio": is_audio,
                    "mtime": stat.st_mtime,
                    "date_str": datetime.fromtimestamp(stat.st_mtime).strftime("%d %b %Y, %H:%M"),
                })
    except Exception as e:
        print("Error listing downloads:", e)
    return files_data


@app.api_route("/file/{task_id}", methods=["GET", "HEAD"])
@app.api_route("/download-file/{task_id}", methods=["GET", "HEAD"])
def download_file_by_id(task_id: str, request: Request):
    """Direct streaming FileResponse for mobile and remote browsers worldwide."""
    target_path: Optional[Path] = None
    filename: Optional[str] = None

    with _lock:
        if task_id in downloads:
            fp = downloads[task_id].get("file_path")
            if fp and Path(fp).exists():
                target_path = Path(fp)
            filename = downloads[task_id].get("filename")

    if not target_path:
        # Fallback search in DOWNLOAD_DIR
        if filename:
            cand = DOWNLOAD_DIR / filename
            if cand.exists():
                target_path = cand
            else:
                stem_sub = re.sub(r"[^\w\s-]", "", Path(filename).stem[:25]).strip().lower()
                for item in DOWNLOAD_DIR.iterdir():
                    if item.is_file() and not item.name.endswith(".part") and not item.name.endswith(".ytdl"):
                        if stem_sub and stem_sub in item.name.lower():
                            target_path = item
                            break

    if not target_path or not target_path.exists():
        raise HTTPException(status_code=404, detail="File not found or has expired from server disk")

    ext = target_path.suffix.lower()
    media_map = {
        ".mp4": "video/mp4",
        ".webm": "video/webm",
        ".mkv": "video/x-matroska",
        ".mp3": "audio/mpeg",
        ".m4a": "audio/mp4",
        ".wav": "audio/wav",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif",
        ".zip": "application/zip",
        ".txt": "text/plain",
    }
    media_type = media_map.get(ext, "application/octet-stream")
    dl_name = filename or target_path.name

    return FileResponse(
        path=str(target_path.resolve()),
        filename=dl_name,
        media_type=media_type,
        method=request.method,
        headers={
            "Cache-Control": "public, max-age=3600",
            "Accept-Ranges": "bytes",
        }
    )


@app.post("/open-file")
def open_file(req: FileActionRequest, request: Request):
    if _is_remote_request(request) or not hasattr(os, "startfile"):
        return {"ok": True, "is_remote": True, "download_url": f"/file/{req.task_id}" if req.task_id else None}

    target_path: Optional[Path] = None
    
    # 1. Try task_id lookup
    if req.task_id and req.task_id in downloads:
        fp = downloads[req.task_id].get("file_path")
        if fp and Path(fp).exists():
            target_path = Path(fp)

    # 2. Try exact filename in DOWNLOAD_DIR
    if not target_path and req.filename:
        cand = DOWNLOAD_DIR / req.filename
        if cand.exists():
            target_path = cand
        else:
            # 3. Fuzzy search by stem
            stem_sub = re.sub(r"[^\w\s-]", "", Path(req.filename).stem[:25]).strip().lower()
            for item in DOWNLOAD_DIR.iterdir():
                if item.is_file() and not item.name.endswith(".part") and not item.name.endswith(".ytdl"):
                    if stem_sub and stem_sub in item.name.lower():
                        target_path = item
                        break

    if not target_path or not target_path.exists():
        raise HTTPException(status_code=404, detail="File could not be found on disk")

    try:
        os.startfile(str(target_path.resolve()))
    except Exception:
        subprocess.Popen(f'explorer "{target_path.resolve()}"', shell=True)
    return {"ok": True, "path": str(target_path)}


@app.api_route("/open-folder", methods=["GET", "POST"])
def open_folder(request: Request, req: Optional[FileActionRequest] = None):
    if _is_remote_request(request) or not hasattr(os, "startfile"):
        return {"ok": True, "is_remote": True}

    target_path: Optional[Path] = None
    if req:
        if req.task_id and req.task_id in downloads:
            fp = downloads[req.task_id].get("file_path")
            if fp and Path(fp).exists():
                target_path = Path(fp)
        if not target_path and req.filename:
            cand = DOWNLOAD_DIR / req.filename
            if cand.exists():
                target_path = cand
            else:
                stem_sub = re.sub(r"[^\w\s-]", "", Path(req.filename).stem[:25]).strip().lower()
                for item in DOWNLOAD_DIR.iterdir():
                    if item.is_file() and not item.name.endswith(".part") and not item.name.endswith(".ytdl"):
                        if stem_sub and stem_sub in item.name.lower():
                            target_path = item
                            break

    if target_path and target_path.exists():
        # Open Windows Explorer and highlight the exact downloaded file!
        subprocess.Popen(f'explorer /select,"{str(target_path.resolve())}"', shell=True)
    else:
        os.startfile(str(DOWNLOAD_DIR.resolve()))
    return {"ok": True}


# ──────────────────────────────────────────────────────────────
# Application Lifecycle
# ──────────────────────────────────────────────────────────────
if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    port = int(os.environ.get("PORT", 8899))
    print(f"\n[OK] Downloadivo Multi-Engine running at http://0.0.0.0:{port}\n")
    if hasattr(os, "startfile") and not os.environ.get("HEADLESS"):
        threading.Timer(1.2, lambda: webbrowser.open(f"http://localhost:{port}")).start()
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="warning")
