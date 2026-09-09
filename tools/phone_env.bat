@echo off
rem The phone-runtime test environment on the laptop: the Python the shell
rem will run (3.11) with exactly the pins in requirements-phone.txt, so a
rem payload can be boot-tested before an APK exists (tools\boot_test.py).
cd /d %~dp0\..
set "PY=%LOCALAPPDATA%\Android\py311full\python.exe"
if not exist "%PY%" (
  echo Python 3.11 is missing at %PY%
  echo It is the interpreter the earlier Android setup installed; put a 3.11 there or edit this file.
  exit /b 1
)
if not exist ".venv-phone\Scripts\python.exe" "%PY%" -m venv .venv-phone
.venv-phone\Scripts\python -m pip install -q --upgrade pip
.venv-phone\Scripts\python -m pip install -r requirements-phone.txt
if errorlevel 1 exit /b 1
rem pytest is a laptop tool, not part of the phone's stack, so it is not in requirements-phone.txt
.venv-phone\Scripts\python -m pip install -q pytest
echo.
echo Ready: .venv-phone
echo   tests:     .venv-phone\Scripts\python -m pytest tests -q
echo   boot test: .venv-phone\Scripts\python tools\boot_test.py
