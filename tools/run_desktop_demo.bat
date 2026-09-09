@echo off
rem The desktop BEGIA on the DEMO data directory (sim_data), for looking at
rem the phone layout in a browser or driving the UDP load against the laptop.
rem Never the live config.json: a demo run must not touch the plant setup.
rem Used by .claude\launch.json ("begia-desktop"); runnable by hand too.
setlocal
set "REPO=%USERPROFILE%\Desktop\IBA-CODE"
set "TRIALREC_DATA_DIR=%REPO%\sim_data"
set "TRIALREC_UI_DIR=%REPO%\ui"
if not exist "%TRIALREC_DATA_DIR%" mkdir "%TRIALREC_DATA_DIR%"
if not exist "%TRIALREC_DATA_DIR%\config.json" copy "%REPO%\sim\sim_config.json" "%TRIALREC_DATA_DIR%\config.json" >nul
cd /d "%REPO%"
echo demo data: %TRIALREC_DATA_DIR%
"%REPO%\.venv\Scripts\python.exe" -m app.main
