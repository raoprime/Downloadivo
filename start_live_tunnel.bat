@echo off
title Downloadivo - Cloudflare Free Live Tunnel
echo ========================================================
echo     Downloadivo - 100%% Free Public Live Tunnel
echo ========================================================
echo.
echo Starting zero-cost Cloudflare Tunnel for http://localhost:8899 ...
echo Log saved to cloudflared.log and URL saved to tunnel_url.txt
echo.
cd /d "%~dp0"
cloudflared tunnel --url http://localhost:8899 --logfile cloudflared.log
pause
