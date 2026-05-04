@echo off
echo Starting Chat Application Backend...
echo.
echo Prerequisites Check:
echo - PostgreSQL should be running on port 5432
echo - Database 'chat_application' should exist
echo - Redis is optional (will use in-memory cache if not available)
echo.
echo If you haven't set up the database yet, run: setup-complete.bat
echo.
cd chat-application-backend\chat-application-backend
echo Running: mvnw spring-boot:run
.\mvnw spring-boot:run
pause