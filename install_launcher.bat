@echo off
REM Installs TvHome.apk on the TV. Put TvHome.apk in this same folder.
if not exist TvHome.apk (
  echo TvHome.apk was not found in this folder.
  pause
  exit /b 1
)
adb push TvHome.apk /data/local/tmp/TvHome.apk
adb shell pm install -r /data/local/tmp/TvHome.apk
adb shell rm /data/local/tmp/TvHome.apk
echo.
echo If you see "Success" above, open "TV Home" from the TV's app list to test it.
pause
