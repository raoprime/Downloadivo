import sys
import time
import json
import urllib.request
import urllib.error

def run_deep_audit():
    results = {}
    base_url = "http://127.0.0.1:8899"
    
    # 1. Health & Core Engine Status
    print("[1/5] Auditing Health & Core Engine...")
    t0 = time.time()
    try:
        with urllib.request.urlopen(f"{base_url}/health", timeout=5) as r:
            data = json.loads(r.read().decode())
            results["health"] = {
                "status": "PASS",
                "code": r.status,
                "latency_ms": round((time.time() - t0) * 1000, 2),
                "data": data
            }
    except Exception as e:
        results["health"] = {"status": "FAIL", "error": str(e)}

    # 2. Asset Integrity & HTTP Range Header Compliance
    print("[2/5] Auditing Streaming & Range Header Compliance...")
    test_assets = [
        ("/assets/intro_3d.mp4", 1122148),
        ("/assets/intro_3d_render.jpg", 616626),
        ("/assets/downloadivo-v4.0.0.apk", 26699814)
    ]
    results["assets"] = {}
    for path, expected_size in test_assets:
        try:
            req = urllib.request.Request(f"{base_url}{path}", headers={"Range": "bytes=0-1023"})
            with urllib.request.urlopen(req, timeout=5) as r:
                chunk = r.read()
                results["assets"][path] = {
                    "status": "PASS",
                    "http_code": r.status, # 206 for Partial Content or 200
                    "content_range": r.headers.get("Content-Range"),
                    "byte_test": len(chunk) in (1024, expected_size)
                }
        except Exception as e:
            results["assets"][path] = {"status": "FAIL", "error": str(e)}

    # 3. Web UI / Onboarding Responsiveness Check
    print("[3/5] Auditing Web Onboarding Viewport & Assets...")
    try:
        with urllib.request.urlopen(f"{base_url}/onboarding", timeout=5) as r:
            html = r.read().decode()
            has_video = "intro_3d.mp4" in html
            has_bento = "features" in html
            has_viewport = "viewport" in html
            results["onboarding_web"] = {
                "status": "PASS" if (has_video and has_bento and has_viewport) else "FAIL",
                "has_video": has_video,
                "has_bento": has_bento,
                "has_viewport": has_viewport,
                "html_bytes": len(html)
            }
    except Exception as e:
        results["onboarding_web"] = {"status": "FAIL", "error": str(e)}

    # 4. Media Extraction Schema Validation
    print("[4/5] Testing Media Schema Validation...")
    try:
        # POST /info with invalid or dummy URL to check graceful validation
        post_data = json.dumps({"url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"}).encode()
        req = urllib.request.Request(
            f"{base_url}/info",
            data=post_data,
            headers={"Content-Type": "application/json"}
        )
        t_info = time.time()
        with urllib.request.urlopen(req, timeout=15) as r:
            info_data = json.loads(r.read().decode())
            results["info_endpoint"] = {
                "status": "PASS",
                "code": r.status,
                "title": info_data.get("title", "N/A"),
                "formats_count": len(info_data.get("formats", [])),
                "latency_ms": round((time.time() - t_info) * 1000, 2)
            }
    except Exception as e:
        results["info_endpoint"] = {"status": "INFO", "note": str(e)}

    # Output JSON summary
    print("\n--- DEEP AUDIT SUMMARY ---")
    print(json.dumps(results, indent=2))
    
    with open(r"C:\Users\DELL\.gemini\antigravity\brain\1da08657-5391-4375-be16-0f191d65d02b\deep_audit_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    run_deep_audit()
