# Launch Mango Music in a visible Android emulator

From any Terminal directory, run this one command:

```sh
/Users/sahilbhardwaj/Developer/MangoMusic-Android/scripts/launch-mango.sh
```

From the project directory, `./scripts/launch-mango.sh` does the same thing. No Gradle build is performed.

The launcher uses the existing Android 16 ARM64 `Mango36` AVD and opens a graphical emulator window with audio enabled. It waits for Android, checks the installed APK against `app/build/outputs/apk/debug/app-debug.apk`, installs with `-r` only when needed, and opens `com.mangomusic/.MainActivity`. App and emulator data are preserved.

## Paths and behavior

- AVD registrations/data: `.local/avd`
- API 36 system image / SDK root: `.tools/sdk`
- Emulator binary: `~/Library/Android/sdk/emulator/emulator` (fallback: project SDK)
- ADB: `~/Library/Android/sdk/platform-tools/adb` (fallback: project SDK)
- Emulator/user state: `.local/emulator`, `.local/android`
- Launch log: `.local/launcher/emulator.log`

Environment variables are scoped to the launcher and child processes: `ANDROID_AVD_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, `ANDROID_EMULATOR_HOME`, `ANDROID_USER_HOME`, and `ADB_VENDOR_KEYS`. No shell profile, Android Studio preferences, shared SDK or macOS app is modified.

The launcher serializes concurrent launches with a file lock and reuses an already-running graphical Mango36 instance. It refuses to create a duplicate when Mango36 is running headless, when port 5556 belongs to another emulator, or when multiple Mango36 instances already exist. Boot timeout is 180 seconds. Missing tools, images, AVD registration, APK, authorization, boot or foreground-activity failures produce actionable errors. It downloads nothing and does not delete data. A cold boot avoids stale snapshots; it does not wipe the device.

Close the emulator window normally when finished. A future launch starts the same AVD and retains the library.

## Diagnosed failures

Android Studio's `AndroidStudio2026.2.2/options/android.sdk.path.xml` selects the **project-local SDK**. The global `Medium_Phone_API_37.0` AVD expects `system-images/android-37.0/google_apis_playstore/arm64-v8a/`, which exists in the **shared SDK**, but not in the SDK selected by Android Studio. This mismatch explains the system-path/API 37 launch error. The API 37 AVD and both SDKs have been left intact.

Mango36 is registered under `.local/avd`, rather than the default `~/.android/avd`; starting it without `ANDROID_AVD_HOME` therefore reports an unknown AVD. Its config also retained `AvdId=Mango`, API 37 target and Google Play tags even though its image points to API 36 Google APIs. Those metadata values were corrected in the project-local `config.ini`. The original is preserved as `.local/avd/Mango36.avd/config.ini.before-visible-launch`.

No app code, APK, previous feature test evidence, installed macOS Mango Music app or macOS reference project was changed. No large components were downloaded and no rebuild was needed.

## Verification on 29 October 2026

- Mango36 booted successfully as `emulator-5556`, Android 16/API 36.
- macOS CoreGraphics reported its main graphical window on-screen (344 × 793 points at x=100, y=100), plus its toolbar window, in one emulator process. The main window was brought forward through AppKit. No graphical confirmation from the user was needed.
- ADB showed `device`; Mango Music was installed and reached `com.mangomusic/.MainActivity` as Android's resumed foreground activity. The captured screen shows the library/search main screen. Startup crash buffer was empty.
- Running the launcher a second time reused the same emulator PID and reported “Matching debug APK is already installed.” No duplicate emulator or reinstall occurred.
- APK SHA-256 still matches the previously recorded artifact; no rebuild occurred. Prior unit/Android test evidence was preserved rather than rerun for this tooling-only change.
- Evidence: `.local/launcher/window-state.json`, `activity.txt`, `crash.log`, and `mango-visible.png`.
- The separate API 37 Device Manager entry remains unchanged and still requires the shared SDK to resolve its image. Use the Mango36 launcher for this app; no manual setup is required for that path.
