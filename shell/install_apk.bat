@echo off
setlocal
cd /d %~dp0
set "ADB=%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" ( echo no APK - run build_apk.bat first & exit /b 1 )
"%ADB%" install -r "%APK%"
if errorlevel 1 exit /b 1
rem by the app's id: its activity keeps the code's own package, which is not
rem the app id, so "id/.MainActivity" would name a class that is not there
"%ADB%" shell monkey -p com.mgvictus.begia -c android.intent.category.LAUNCHER 1 >nul
echo.
echo Watching the log (Ctrl+C to stop): ..\tools\logcat.bat
