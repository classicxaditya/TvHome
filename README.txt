TV HOME 2.0 - Customizable Android TV launcher
==============================================

WHAT'S NEW IN 2.0
- Material You-inspired theme picker: Lavender, Ocean, Aurora, Sunset and Classic Black.
- RAM widget: current system RAM usage, total RAM and percent used, refreshed every 15 seconds.
- Wi-Fi widget: displays the connected network name when Android grants the required permission.
- Google Assistant shortcut: tries the TV's available ASSIST / VOICE_COMMAND activity. Availability depends on the TV firmware and Google app setup.
- Animated app transitions and a scale/fade return animation; animations can be switched off.
- The focused tile has a theme-coloured corner/edge glow with Soft, Bright and Neon intensity choices.
- Settings menu lets you change themes, show/hide RAM and Wi-Fi widgets, and adjust motion/glow.
- Existing hold-OK dock customization remains: add, remove and move up to 7 apps.

ON THE TV
- D-pad moves focus. OK opens an app. Hold OK on an app to edit the dock.
- Up from the dock moves to the top bar: Inputs, Assistant, Wi-Fi, Settings.
- Wi-Fi and RAM widgets appear below the date by default. The Wi-Fi SSID may show as unavailable on some Android builds unless location permission and system privacy rules allow it.
- Settings opens theme, widget, motion/glow, app refresh and dock reset options.
- Assistant shortcut tries Android's standard assistant/voice actions; some OEM builds may route to a different assistant or may not expose an assistant activity.

BUILD WITH GITHUB ACTIONS
1. Replace the existing project files in your repository with the files from this folder. Keep your repository's existing .github/workflows/build.yml workflow if you already created it.
2. Commit the changes. Open Actions and wait for the Build APK workflow to finish.
3. Download the TvHome-apk artifact and extract it. The APK inside is app-debug.apk; rename it TvHome.apk.

INSTALL / UPDATE
1. Put TvHome.apk next to adb.exe in your platform-tools folder.
2. Make sure `adb devices` shows your TV as `device`.
3. Double-click install_launcher.bat. It uses `adb install -r` to update the existing app.
4. Open TV Home from the TV's app list and test it first. Do not set it as default until the buttons and apps work.

DEFAULT HOME / RECOVERY
- set_home.bat attempts to set TV Home as default. Some Xiaomi firmware ignores that request; on this TV, disabling Google TV Launcher previously made the custom launcher appear on Home.
- undo_home.bat re-enables Google TV Launcher and attempts to set it as default again.
- Keep Google TV Launcher enabled while testing this new build. Only change the default Home setup after verifying widgets, Assistant, Inputs and app launches.

NOTES
- RAM usage is the system-wide Android memory estimate, not a per-app RAM meter.
- The Wi-Fi name is subject to Android's SSID privacy restrictions and may show a generic label if permission/device firmware does not expose it.
- Material You here means a native, Material-inspired tonal palette, not the full Android dynamic-colour system.
- Launcher actions and Assistant launch behaviour have not been tested on every TV firmware. Test this build before making it the default launcher.
