@echo off
setlocal
cd /d "%~dp0"
if exist "C:\java\oracleJdk-25\bin\java.exe" set "JAVA_HOME=C:\java\oracleJdk-25"
echo [OAC] Building for Minecraft 1.21.11...
call gradlew.bat clean build --no-configuration-cache
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Please send the full console log.
  pause
  exit /b 1
)
echo.
echo [SUCCESS] Find the mod JAR in build\libs\
pause
