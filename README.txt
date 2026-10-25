TV HOME - Apple TV style launcher for your Redmi TV
====================================================

HOW TO USE IT ON THE TV
- Arrow keys move around. OK opens an app.
- Hold OK on an app to change the dock:
    on a dock app: Move left, Move right, Remove from dock
    on a grid app: Add to dock (up to 7 apps)
  The grid below the dock always uses the same number of columns as the dock (minimum 4).
- Up from the dock goes to the top bar (Inputs, Wi-Fi, Settings).
- Inputs opens a small list of HDMI ports, AV and Live TV.
- The Home button brings you back to the dock.

HOW TO BUILD THE APK (no coding needed)
Option A - GitHub, nothing to install on your PC
 1. Create a free account at github.com and make a new repository (private is fine).
 2. Open the repository, click "Add file" > "Upload files".
 3. Open this folder on your PC, select EVERYTHING inside it (including the hidden-looking
    ".github" folder) and drag it into the browser page. Click "Commit changes".
 4. Click the "Actions" tab. A job called "Build APK" runs for about 4-6 minutes.
 5. Open the finished job, scroll to "Artifacts" and download "TvHome-apk".
 6. Unzip it. You get app-debug.apk. Rename it to TvHome.apk.

Option B - Android Studio on your PC
 1. Install Android Studio, choose File > Open, pick this folder, wait for it to finish syncing.
 2. Choose Build > Build Bundle(s) / APK(s) > Build APK(s).
 3. The APK is in app\build\outputs\apk\debug\app-debug.apk. Rename it to TvHome.apk.

INSTALL ON THE TV
 1. Put TvHome.apk in your platform-tools folder, next to adb.exe.
 2. Make sure adb is connected to the TV (adb devices shows it).
 3. Double-click install_launcher.bat. The launcher appears in the TV's app list as "TV Home".
 4. Open it from the app list first and check it looks right. Only then run set_home.bat.

GO BACK TO THE OLD HOME SCREEN AT ANY TIME
 Double-click undo_home.bat.

CHANGE THINGS LATER
 - Dock order and contents: hold OK on an app (see above).
 - Wallpaper: the background is plain black on purpose. To change it, edit
   root.setBackgroundColor(Color.BLACK) in MainActivity.java and rebuild.
 - Font: Outfit (SIL Open Font License, see app/src/main/assets/fonts/OFL.txt).
