@echo off
setlocal
cd /d %~dp0
rem Builds app\build\outputs\apk\debug\app-debug.apk.
rem Needs: JDK 17, the Android SDK (platform 34, build-tools 34.0.0), a Python
rem 3.8+ for pip at build time, and a payload from the desktop repo's
rem build_payload.bat. The Gradle it needs downloads itself (the wrapper).
set "JAVA_HOME=%LOCALAPPDATA%\Android\jdk-17"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist local.properties (
  > local.properties echo sdk.dir=%ANDROID_HOME:\=\\%
  >> local.properties echo begia.buildPython=%LOCALAPPDATA:\=\\%\\Android\\py311full\\python.exe
  >> local.properties echo # begia.payload=..\\..\\Desktop\\IBA-CODE\\dist\\begia-payload.begia
  echo   wrote local.properties - edit it if your SDK, Python or payload live elsewhere
)
call gradlew.bat assembleDebug %*
if errorlevel 1 (
  echo.
  echo BUILD FAILED - read the output above.
  exit /b 1
)
echo.
echo APK: %~dp0app\build\outputs\apk\debug\app-debug.apk
echo Install on the connected phone with install_apk.bat
