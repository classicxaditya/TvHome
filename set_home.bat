@echo off
REM Makes TV Home the default home screen. Run only after testing it from the app list.
adb shell cmd package set-home-activity com.adiii.tvhome/.MainActivity
echo.
echo Now press the Home button on the TV remote, then run: adb shell dumpsys activity activities ^| findstr mFocusedApp
pause
