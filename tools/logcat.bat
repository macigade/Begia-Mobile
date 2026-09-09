@echo off
rem Only what BEGIA says: the shell, Python's stdout/stderr, and crashes.
"%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe" logcat -v time -s begia.shell python.stdout python.stderr AndroidRuntime
