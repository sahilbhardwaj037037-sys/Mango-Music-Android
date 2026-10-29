# Mango Music for Android

Native Kotlin / Jetpack Compose / Material 3 music player. Android 8.0+ (API 26).

The current UI uses a compact original-logo header, hamburger drawer, neutral Light/Dark surfaces and restrained red accents. [Redesign screenshots and validation](docs/REDESIGN.md). The latest [vinyl center fix and validation](docs/VINYL_ALIGNMENT.md) preserves the original logo and existing library. [Experimental on-device online audio](docs/ONLINE_AUDIO.md) now provides real search and native Media3 streaming for permitted public content, without a video player or API key. ARM64 build; provider compatibility is experimental.

## Launch the graphical emulator

```sh
./scripts/launch-mango.sh
```

Starts or reuses Mango36, waits for Android, installs the existing APK only if needed, and opens Mango Music. [Launcher details and emulator diagnosis](docs/LAUNCH.md).

## Build

Open this directory in Android Studio, or run:

```sh
./scripts/gradle-env.sh assembleDebug
./scripts/gradle-env.sh testDebugUnitTest lintDebug
./scripts/gradle-env.sh -I scripts/isolated-tests.gradle :app:connectedDebugAndroidTest
./scripts/gradle-env.sh assembleDebug  # restore the normal APK after isolated testing
```

`scripts/gradle-env.sh` uses Android Studio's existing JBR and the Gradle Wrapper. SDK and caches are local to `.tools/` and `.local/`. `local.properties` points at the project-local SDK. When opening elsewhere, select an installed SDK in Android Studio and a JDK compatible with Gradle 9.1 (17–25). API 36, build tools 36.0.0, and accepted Android SDK licenses are required. No global Gradle installation is needed.

Instrumentation uses a separate `com.mangomusic.validation` sandbox so the existing library is preserved. Rebuild without the init script before launching the normal app. The launcher itself is unchanged.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Use

- Use the hamburger drawer for Search, Up Next, Favorites, History, Downloads and Settings.
- Search the local library and three bundled original demos, or select Online to search permitted public YouTube audio by title/artist/keywords.
- Import audio using Android's document picker. Imported files are copied into the app and remain available offline.
- Add a direct HTTPS audio link to stream your own audio. Check the download permission box only for files you are authorized to save, then use the track menu to download.
- Tap a track to play the visible list; use its menu to enqueue or play next. Up Next supports selecting/removing queue entries.
- Tap the persistent mini player to open Now Playing. The branded vinyl turns during actual playback and keeps its angle while paused.
- Use Settings for System/Light/Dark and Offline Mode. Switching offline stops and clears the current queue.
- Favorites and listening history are stored in Room. Background playback uses a Media3 service, media notification, audio focus and headphone-disconnect handling.

Experimental YouTube search/audio resolution runs on-device using pinned yt-dlp, Python and QuickJS. No YouTube downloading or account access is provided. Provider restrictions and maintenance requirements apply; Apple Music and Spotify are not connected. [Integration decisions](docs/INTEGRATIONS.md), [feature checklist and progress](docs/PROGRESS.md), and [validation evidence](docs/VALIDATION.md) describe the practical limits.

## Structure

- `domain/`: track metadata, catalog filtering and disc motion math.
- `data/`: Room database, audio imports and authorized download jobs.
- `playback/`: service-owned ExoPlayer, transient online media sources and MediaSession.
- `online/` and `:online-runtime`: catalog/resolver boundary and isolated embedded Python/JS runtime.
- `ui/`: lifecycle-aware Compose UI and ViewModel/MediaController.

No macOS application files are required at runtime. The macOS reference was read-only throughout development.
