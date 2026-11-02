@echo off
title Downloadivo - Cloudflare Free Live Tunnel
echo ========================================================
echo     Downloadivo - 100%% Free Public Live Tunnel
echo ========================================================
echo.
echo Starting zero-cost Cloudflare Tunnel for http://localhost:8899 ...
echo Look below for your public https://*.trycloudflare.com link!
echo.
cloudflared tunnel --url http://localhost:8899
pause
