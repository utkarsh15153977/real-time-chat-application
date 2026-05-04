@echo off
echo PostgreSQL Setup Guide
echo =====================
echo.
echo 1. Download PostgreSQL from: https://www.postgresql.org/download/windows/
echo 2. Install with these settings:
echo    - Port: 5432
echo    - Username: postgres  
echo    - Password: newpassword
echo    - Database: (leave default)
echo.
echo 3. After installation, create the database:
echo    - Open pgAdmin or psql
echo    - Run: CREATE DATABASE chat_application;
echo.
echo 4. Test connection:
echo    psql -U postgres -d chat_application
echo.
echo Alternative: Use Docker
echo docker run --name postgres-chat -e POSTGRES_PASSWORD=newpassword -e POSTGRES_DB=chat_application -p 5432:5432 -d postgres:13
echo.
pause