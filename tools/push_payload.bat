@echo off
setlocal
rem The ten-second loop: push a payload to the phone over adb and let the
rem shell's dev server (debug builds only) install it and restart the recorder.
rem   tools\push_payload.bat [path\to\file.begia]
set "ADB=%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe"
set "PAYLOAD=%~1"
if "%PAYLOAD%"=="" set "PAYLOAD=%USERPROFILE%\Desktop\IBA-CODE\dist\begia-payload.begia"
if not exist "%PAYLOAD%" ( echo no payload at %PAYLOAD% - run build_payload.bat in the desktop repo & exit /b 2 )
"%ADB%" forward tcp:8081 tcp:8081 >nul
if errorlevel 1 ( echo adb could not reach the phone & exit /b 1 )
curl -sS -T "%PAYLOAD%" http://127.0.0.1:8081/payload
echo.
rem the phone's BEGIA, from the laptop's browser: handy while the UI is being worked on
"%ADB%" forward tcp:18080 tcp:8080 >nul
echo The phone's BEGIA is also at http://127.0.0.1:18080 on this laptop while the phone is plugged in.
