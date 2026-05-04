@echo off
echo Restarting Chat Application Backend...
echo.
echo Security configuration updated to allow all /api/** endpoints
echo.
cd chat-application-backend\chat-application-backend
echo Stopping any existing backend process...
taskkill /f /im java.exe 2>nul
echo.
echo Starting backend with new security config...
.\mvnw spring-boot:run
pause