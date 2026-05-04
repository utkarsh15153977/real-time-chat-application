@echo off
echo Redis Setup Guide
echo =================
echo.
echo Option 1: Download Redis for Windows
echo 1. Download from: https://github.com/microsoftarchive/redis/releases
echo 2. Extract and run redis-server.exe
echo 3. Default port: 6379
echo.
echo Option 2: Use Docker (Recommended)
echo docker run --name redis-chat -p 6379:6379 -d redis:alpine
echo.
echo Option 3: Use WSL (Windows Subsystem for Linux)
echo wsl
echo sudo apt update
echo sudo apt install redis-server
echo redis-server
echo.
echo Test Redis connection:
echo redis-cli ping
echo (Should return: PONG)
echo.
pause