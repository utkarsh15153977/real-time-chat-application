@echo off
echo Starting Chat Application Backend (Development Mode)...
echo.
echo Using H2 in-memory database (no PostgreSQL required)
echo No Redis required for this mode
echo.
cd chat-application-backend\chat-application-backend
echo Running with development profile...
.\mvnw spring-boot:run -Dspring-boot.run.profiles=dev
pause
