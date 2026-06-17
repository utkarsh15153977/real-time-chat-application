@echo off
echo Fixing Maven Dependencies...
echo ========================
echo.

echo Step 1: Cleaning Maven local repository cache...
cd chat-application-backend\chat-application-backend
.\mvnw dependency:purge-local-repository -DactTransitively=false -DreResolve=false

echo.
echo Step 2: Cleaning project...
.\mvnw clean

echo.
echo Step 3: Downloading dependencies (this may take a few minutes)...
.\mvnw dependency:resolve

echo.
echo Step 4: Compiling project...
.\mvnw compile

echo.
echo If successful, you can now run: start-backend-simple.bat
pause