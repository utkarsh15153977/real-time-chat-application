@echo off
echo Starting Chat Backend with Gradle...
echo ===================================
echo.

cd chat-application-backend\chat-application-backend

echo Step 1: Download Gradle Wrapper (if needed)
if not exist gradlew.bat (
    echo Downloading Gradle Wrapper...
    curl -o gradle-wrapper.zip https://services.gradle.org/distributions/gradle-8.5-bin.zip
    powershell -command "Expand-Archive gradle-wrapper.zip -DestinationPath gradle"
    gradle\bin\gradle wrapper
)

echo Step 2: Clean and build
.\gradlew clean build

echo Step 3: Start application
.\gradlew bootRun

pause