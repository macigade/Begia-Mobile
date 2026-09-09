@echo off
setlocal
cd /d %~dp0
set "ADB=%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" ( echo no APK - run build_apk.bat first & exit /b 1 )
"%ADB%" install -r "%APK%"
if errorlevel 1 exit /b 1
"%ADB%" shell am start -n com.sarralle.begia/.MainActivity
echo.
echo Watching the log (Ctrl+C to stop): ..\tools\logcat.bat
