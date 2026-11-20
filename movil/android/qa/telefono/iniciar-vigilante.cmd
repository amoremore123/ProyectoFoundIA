@echo off
rem Inicia en segundo plano (oculto) el vigilante de tuneles adb reverse de FoundIA.
rem Si ya esta corriendo, la segunda copia termina sola.
start "" /B "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0vigilar-tuneles.ps1"
echo Vigilante de tuneles iniciado.
