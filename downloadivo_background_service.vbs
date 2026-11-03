' Downloadivo Silent 24/7 Background Service Launcher
' Runs both Python Server and Cloudflare Tunnel invisibly without console windows

Set WshShell = CreateObject("WScript.Shell")

' 1. Start Downloadivo Python Engine in Background (Hidden)
WshShell.Run "cmd /c cd /d C:\Users\DELL\.gemini\antigravity\scratch\FastDownloader && C:\Users\DELL\AppData\Local\Python\pythoncore-3.14-64\python.exe main.py", 0, False

' 2. Wait 2 seconds for server to bind port 8899
WScript.Sleep 2000

' 3. Start Cloudflare Live Tunnel in Background (Hidden)
WshShell.Run "cmd /c cloudflared tunnel --url http://localhost:8899", 0, False
