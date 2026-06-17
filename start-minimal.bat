@echo off
echo Starting Minimal Chat Backend...
echo ================================
echo.

echo Step 1: Backup current pom.xml
cd chat-application-backend\chat-application-backend
copy pom.xml pom-backup.xml

echo Step 2: Use simplified pom.xml
copy pom-simple.xml pom.xml

echo Step 3: Clean and compile
.\mvnw clean compile

echo Step 4: Start application
.\mvnw spring-boot:run

pause