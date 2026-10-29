# Android V1 validation — 29 October 2026

This document preserves the original V1 baseline. The current UI/APK validation and checksum are in [REDESIGN.md](REDESIGN.md).

## Build and tooling

Apple Silicon macOS host. Existing Android Studio 2026.2 bundled JBR 25.0.4 was reused. Gradle Wrapper 9.1.0 and AGP 9.0.1 compile API 36 with Java/Kotlin target 17. No global Gradle/JDK installation was needed. API 36 and an Android 16 ARM64 image were downloaded from Google into `.tools/`; checksums matched official repository metadata. Existing accepted licenses were copied into the project SDK. ADB/emulator binaries came from the existing Android SDK.

SDK files, dependency caches, debug signing key and writable AVD data are project-local. Android tooling nevertheless generated standard per-user ADB keys and analytics metadata during emulator startup. No macOS Mango Music source, Git state, Xcode project, installed app, database, preferences, backup or release artifact was modified.

Build commands:

```sh
./scripts/gradle-env.sh assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
./scripts/gradle-env.sh connectedDebugAndroidTest
# Rerun the affected test after the compact-window/accessibility improvements:
./scripts/gradle-env.sh assembleDebug lintDebug connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mangomusic.NavigationTest
```

## Automated results

**Six JVM unit tests passed**, covering title/artist search, whitespace/case/deduplication, invalid queries, time formatting, vinyl pause/resume, angle wrapping and long-frame clamping. [JUnit evidence](test-evidence/unit-tests.xml).

**Eight Android integration tests passed on Android 16 / API 36 ARM64**, with no skipped tests. [Full run log](test-evidence/android16-full-suite.log).

| Check | Verified behavior |
|---|---|
| Local import | Original WAV copied into private storage, duration extracted as 24,000 ms, local file exists |
| Room | Favorite, play count and history survive DB close/reopen; reseeding preserves state; clearing history preserves favorite |
| Navigation | Search empty state, clearing search, favorite selection, mini player, Now Playing, vinyl presence and Light/Dark appearance |
| Audio focus | Transient focus loss pauses playback; abandoning transient focus resumes it |
| Authorized download | JesseW’s licensed Example.ogg downloaded with DownloadManager, validated, then played from its file URI; test copy deleted |
| Playback lifecycle | Bundled audio actually advances, pause freezes position, seek/resume work, background position advances and media notification exists |
| Playback modes | Repeat One loops, Repeat All wraps, next and previous select expected tracks; listening history recorded |
| HTTPS/error | Google public MP3 actually plays and reports duration; missing asset generates player error |

The final navigation test was rerun after compact layout and Offline Mode accessibility changes. The added offline toggle assertions check on/off state. [Final navigation JUnit result](test-evidence/final-navigation.xml).

Lint completed with **0 errors**. Remaining warnings are dependency/Gradle update suggestions, KTX style suggestions, and the exported media-service warning. The service is exported for system media integration and explicitly rejects controllers outside the app unless Media3 marks them trusted. Newer library versions were not required for this tested stable combination.

Android 17 / API 37 ARM64 also passed Room, playback lifecycle/modes, HTTPS playback and missing-source checks. Its navigation test failed in Espresso before app interaction with `NoSuchMethodException: InputManager.getInstance`. The full suite was moved to Android 16, where it passed; this harness failure was not counted as an app success.

## Visual / manual checks

- App launched and rendered on Android 16 and 17 emulators; no launch crash observed.
- Actual screenshot comparisons show disc rotation during playback, frozen angle while paused, and rotation after resume. Paused disc pixels differ by at most 3 channel levels from renderer dithering, with zero pixels above that threshold; playing screenshots show substantial rotation. Domain tests separately verify angle continuity across pause/resume.
- Rotation runs through graphics-layer updates, outside composition. Its coroutine exists only for visible Now Playing while lifecycle is STARTED and Media3 reports actual playback.
- System/Light/Dark appearance works. System bar contrast follows the selected theme.
- Compact player can scroll in landscape; wide windows use two panes.
- **Offline Mode passed with emulator Wi-Fi and cellular disabled:** bundled audio reached PLAYING and advanced. Connectivity and portrait orientation were restored afterward. [Offline result](test-evidence/offline-visual.json).
- Landscape library/player scroll checks passed; playback controls were reachable. [Landscape controls](screenshots/landscape-controls.png), [dark Now Playing](screenshots/now-playing-dark.png), [offline library](screenshots/home-dark-offline.png).

## Practical limits

Search is library/demo filtering. No YouTube/Apple Music/Spotify provider was connected. Streaming success refers to directly playable HTTPS audio, not commercial/provider search or protected streams. Download success refers to the authorized test recording, not arbitrary or protected provider URLs. [Integration sources and restrictions](INTEGRATIONS.md).

No physical device, real Bluetooth/headset disconnect, OEM battery policy, release signing or store-distribution validation was performed. Notification existence and MediaController background commands were tested; every physical lock-screen/OEM interaction was not. Queue survives the active service session, not app process death. The emulator used `-no-audio`, so player position/state/decoding were verified, not audible output through host speakers.

## Final debug APK

- Exact path: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk`
- Size: 24058747 bytes (22.94 MiB).
- SHA-256: `0bf13d70e5f196cf3632eb1ca5ea37191471b94e812c4cfb1a735116d356cdc9`
- **Actually installed and launched on the isolated Android 16 ARM64 emulator**, then exercised in Offline Mode and landscape after the final build. Earlier builds also ran on Android 17 ARM64. No physical device was connected/tested.
- APK signature verified with apksigner (v2); manifest confirms package `com.mangomusic`, min SDK 26 and target SDK 36.
- Debug signed; not a release/store artifact. The emulator remains available with this APK installed and playback paused.
