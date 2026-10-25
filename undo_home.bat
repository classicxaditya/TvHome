@echo off
REM Puts the original Google TV home screen back.
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
echo.
echo Done. Press the Home button on the TV remote.
pause
