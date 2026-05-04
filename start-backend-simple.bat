@echo off
echo Starting Chat Application Backend (Simple Mode)...
echo.
echo ✅ Using H2 in-memory database (no installation required)
echo ✅ No Redis required
echo ✅ No external dependencies
echo.
echo The database will be created automatically in memory.
echo All data will be lost when the application stops.
echo.
cd chat-application-backend\chat-application-backend
echo Starting Spring Boot application...
.\mvnw spring-boot:run -Dspring-boot.run.profiles=simple
pause
