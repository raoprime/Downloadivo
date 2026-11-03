@echo off
title Stop Downloadivo
echo Stopping Downloadivo Background Services...
taskkill /F /IM cloudflared.exe 2>nul
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8899 ^| findstr LISTENING') do taskkill /F /PID %%a 2>nul
echo.
echo All Downloadivo background services have been stopped.
timeout /t 3 >nul
