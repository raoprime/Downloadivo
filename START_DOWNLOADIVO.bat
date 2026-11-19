@echo off
title Downloadivo Auto-Launcher
cd /d "C:\Users\DELL\.gemini\antigravity\scratch\FastDownloader"

echo [1/4] Starting backend server...
start /min "" "C:\Users\DELL\AppData\Local\Python\pythoncore-3.14-64\python.exe" main.py
timeout /t 3 /nobreak >nul

echo [2/4] Starting Cloudflare tunnel...
start /min "" "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8899 --logfile cloudflared.log --no-autoupdate

echo [3/4] Waiting for tunnel URL (30s)...
timeout /t 30 /nobreak >nul

echo [4/4] Extracting tunnel URL and updating Vercel...
powershell -Command ^
  "$url = (Get-Content cloudflared.log | Select-String 'trycloudflare' | Select-Object -Last 1).Line; ^
   if ($url -match 'https://([a-z0-9-]+\.trycloudflare\.com)') { ^
     $tunnel = $matches[1]; ^
     Write-Host 'Tunnel: ' $tunnel; ^
     $j = Get-Content vercel.json -Raw; ^
     $old = [regex]::Match($j, '[a-z0-9-]+\.trycloudflare\.com').Value; ^
     if ($old -ne $tunnel) { ^
       $j = $j -replace $old, $tunnel; ^
       $j | Set-Content vercel.json -Encoding UTF8; ^
       git add vercel.json; ^
       git commit -m ('fix(tunnel): auto-update to ' + $tunnel); ^
       git push origin main; ^
       Write-Host 'Vercel updated! Site live in 30s.' ^
     } else { Write-Host 'Tunnel URL unchanged, no push needed.' } ^
   } else { Write-Host 'ERROR: Could not find tunnel URL in log!' }"

echo.
echo Downloadivo is LIVE! downloadivo.vercel.app
pause
