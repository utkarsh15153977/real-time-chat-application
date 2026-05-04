@echo off
echo Chat Application Complete Setup
echo ===============================
echo.

echo Step 1: PostgreSQL Setup
echo ------------------------
echo 1. Install PostgreSQL from: https://www.postgresql.org/download/windows/
echo    - Use port 5432
echo    - Username: postgres
echo    - Password: newpassword
echo.
echo 2. Create database:
echo    - Open pgAdmin or Command Prompt
echo    - Run: createdb -U postgres chat_application
echo    - Or use the SQL file: create-database.sql
echo.

echo Step 2: Redis Setup (Optional but Recommended)
echo ----------------------------------------------
echo Option A - Docker (Easiest):
echo    docker run --name redis-chat -p 6379:6379 -d redis:alpine
echo.
echo Option B - Direct Install:
echo    Download from: https://github.com/microsoftarchive/redis/releases
echo    Extract and run redis-server.exe
echo.

echo Step 3: Verify Services
echo -----------------------
echo PostgreSQL: psql -U postgres -d chat_application
echo Redis: redis-cli ping (should return PONG)
echo.

echo Step 4: Start Application
echo -------------------------
echo Run: start-backend.bat
echo Then: start-frontend.bat
echo.

echo The application will work with just PostgreSQL.
echo Redis is optional - it will use in-memory cache if Redis is not available.
echo.
pause