@echo off
if exist "%~dp0ElectroBot.exe" (
    start "" "%~dp0ElectroBot.exe"
    exit /b 0
)
pythonw "%~dp0run.py"
