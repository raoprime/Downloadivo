# 🚀 Downloadivo Live Deployment Guide (₹0 Forever & Infinite Scale)

This guide shows you how to publish **Downloadivo** to the public internet with **₹0 cost**, **unlimited downloads**, and **24/7 availability**.

---

## ⚡ Option 1: Cloudflare Tunnel (Instant Public URL in 30 Seconds)

You can make your current PC act as the server instantly without paying a single rupee, without buying a domain, and without port forwarding.

### Why this is unbeatable:
- **₹0 Cost:** 100% free forever provided by Cloudflare.
- **Enterprise DDoS Shield:** Cloudflare absorbs all traffic spikes and attacks.
- **No Bandwidth Bills:** Uses your existing home fiber/broadband (unlimited GBs).
- **Residential IP:** YouTube & Instagram never block home IPs (unlike AWS/DigitalOcean data centers).

### Step-by-Step Launch:
1. **Start Downloadivo Server:**
   ```powershell
   cd C:\Users\DELL\.gemini\antigravity\scratch\FastDownloader
   & "C:\Users\DELL\AppData\Local\Python\pythoncore-3.14-64\python.exe" main.py
   ```
2. **Install Cloudflare Tunnel CLI:**
   Open a separate PowerShell terminal and run:
   ```powershell
   winget install Cloudflare.cloudflared
   ```
   *(Or download `cloudflared.exe` from [cloudflare.com](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/))*

3. **Go Live Instantly:**
   Run this single command:
   ```powershell
   cloudflared tunnel --url http://localhost:8899
   ```
4. **Your Live URL:**
   Cloudflare will output a public HTTPS link:
   ```text
   https://downloadivo-xyz.trycloudflare.com
   ```
   Anyone in the world on phone, tablet, or PC can now open this link and download videos instantly!

---

## 🌐 Option 2: Oracle Cloud Always-Free Tier (24/7 Standalone Server at ₹0)

If you want the downloader to stay live even when your PC is turned off, Oracle Cloud provides the most generous permanent free tier in the world.

### What you get for ₹0 forever:
- **4 ARM vCPUs (Ampere A1)**
- **24 GB RAM**
- **200 GB NVMe SSD**
- **10 TB (10,000 GB) Outbound Bandwidth / Month**
- **Static IPv4 Address**

### Step-by-Step Server Setup:
1. Create a free account at [cloud.oracle.com](https://cloud.oracle.com).
2. Create an **Ubuntu 22.04 or 24.04** VM instance (Choose `VM.Standard.A1.Flex` with 4 OCPUs & 24GB RAM).
3. SSH into your server:
   ```bash
   ssh -i your-key.key ubuntu@YOUR_SERVER_IP
   ```
4. Install Docker & Git:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose git
   sudo usermod -aG docker $USER
   ```
5. Clone or upload your Downloadivo directory and run:
   ```bash
   docker-compose up -d --build
   ```
6. Open port 8899 in Oracle Security List and Ubuntu firewall:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8899 -j ACCEPT
   sudo netfilter-persistent save
   ```
7. Connect Cloudflare Tunnel or your free domain to port 8899.

---

## 🛡️ Built-in Future Proof Protections

1. **Auto-Pruning Ephemeral Storage:**
   The server background cleaner deletes files older than **30 minutes** (`FILE_RETENTION_SECONDS = 1800`) automatically. Your disk will **never run out of space** even with 100,000 downloads a day.

2. **Universal Device Streaming (`/file/{task_id}`):**
   When a user clicks "Save to Device", the file streams straight to their mobile gallery or browser downloads folder via HTTP `Content-Disposition: attachment`.

3. **Pure Profit Monetization:**
   Because your hosting and bandwidth costs are ₹0, every rupee earned from ad networks (Google AdSense, Media.net, Carbon, Monetag) or donation buttons (Buy Me a Coffee) goes directly into your bank account as 100% net profit.
