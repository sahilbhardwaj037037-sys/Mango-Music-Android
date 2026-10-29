# Mango Music Android V1

Current implementation: [experimental on-device online audio](ONLINE_AUDIO.md) and optional [Mix / continuous autoplay](MIX_AUTOPLAY.md). Earlier milestone entries below are historical.

Updated 29 October 2026. Reference `/Users/sahilbhardwaj/Developer/MangoMusic` inspected read-only. No macOS source, Git state, application, library data or release artifacts changed.

## Milestones
1. **Environment ready.** Apple Silicon; Android Studio 2026.2; existing bundled JBR 25.0.4. Gradle Wrapper 9.1.0, AGP 9.0.1, built-in Kotlin 2.2.10, Compose compiler 2.2.10, Compose BOM 2025.08.01, Media3 1.8.0, Room 2.7.2 and KSP 2.3.4. API 36/platform tools provisioned inside project. Existing accepted SDK licenses reused; no GUI setup or new acceptance required. Standalone sdkmanager/global Gradle unnecessary.
2. **Android project builds.** Gradle Kotlin DSL, min API 26, target/compile API 36, Java/Kotlin bytecode 17. Official Gradle distribution and SDK/image downloads checksum verified. SDK, caches, debug signing key and isolated AVD data stored in this workspace; no global software installed. Android tooling did generate standard per-user ADB keys/analytics metadata during emulator startup despite project-local path settings; those side-effect files were left intact.
3. **UI/navigation working.** Compose Material 3; System/Light/Dark; pink accents; Search, Up Next, Favorites, History, Downloads; mini player and Now Playing; wide two-pane layout and scrollable compact player.
4. **Playback/persistence working.** Service-owned Media3 ExoPlayer, background media session/notification, focus and noisy-headphone handling; Room library. Native local import and direct HTTPS audio.
5. **Feature integration/testing completed.** Six unit tests and eight Android 16 integration tests passed. Android 17 also passed four non-UI tests; its stable Espresso harness has an InputManager incompatibility. Navigation rerun after final layout changes. Visual animation/offline checks recorded in VALIDATION.md.
6. **Debug APK generated.** `app/build/outputs/apk/debug/app-debug.apk`. Installed and exercised on isolated ARM64 emulators. See VALIDATION.md for exact evidence, limitations and final artifact checksum.

## Reference parity

| Area | macOS reference | Android V1 |
|---|---|---|
| Search | Validated YouTube query, yt-dlp results, loading/empty/retry | Library filtering plus separate debounced online yt-dlp search with real titles, artists and thumbnails; loading/empty/retry states. Experimental, permitted public content only. |
| Controls | Play/pause/stop, previous/next, seek, volume | Play/pause/stop, previous/next and seeking; device volume keys; replay after end. |
| Queue/modes | Up Next, enqueue/play-next, repeat, favorites sequence | Media3 queue, play-next/enqueue/remove/select; Repeat Off/One/All and Shuffle; Play All favorites. Queue is session-only. |
| Favorites/history | SQLite, play count, retention controls | Room favorites, last listened time and play counts; clear-history confirmation. Retention settings deferred. |
| Downloads/offline | yt-dlp extraction/conversion, managed files, offline mode | Authorized direct HTTPS downloads via DownloadManager, validation and local playback; private audio import and bundled originals; Offline Mode. No YouTube downloading or conversion. |
| Appearance/navigation | System/Light/Dark, pink, sidebar, playback bar | Native Material 3, responsive navigation, persistent mini player and branded vinyl. |
| Errors | Search, playback, download and persistence errors/retries | Visible errors, playback retry, download retry via menu, loading/empty states. |
| Metadata | Stable ID, title, artist, known/unknown duration | Stable IDs; title/artist/duration from imported files or user links; system media metadata. No artwork on vinyl. |
| Animation | Reference-inspired player | Vinyl spins only during actual playback while visible and lifecycle STARTED; pauses in place and resumes without resetting. |

## Limitations / next steps

- Experimental public YouTube search and native audio resolution are implemented; no account/sign-in integration, YouTube downloading, Apple Music or Spotify catalog. Provider changes can break extraction.
- Distribution and commercial use need separate provider/licensing review. See ONLINE_AUDIO.md for runtime versions, maintenance and test limits.
- No DRM/access-restriction bypass, mpv dependency, invented credentials, external backend or paid API. The Android on-device runtime is isolated from the desktop implementation.
- No custom playlists, queue restoration after process death, history retention options or download deletion/cancellation UI. Related-song Mix is now implemented; recommendation relevance and extended sessions need further manual review.
- Physical devices, Bluetooth routing, real headset unplugging, OEM battery restrictions, release signing and store distribution remain untested.
- This is a debug build for testing. App data is excluded from backup; uninstall removes the library and imports.

## Graphical emulator launcher — 29 October 2026

Resolved the SDK/AVD lookup mismatch for Mango36 and corrected its stale API 37/Google Play metadata, preserving the original config and all device data. Added `scripts/launch-mango.sh` / `launch-mango.py` with explicit project-local SDK/AVD environment, GUI startup, boot waiting, duplicate prevention, APK matching and foreground launch verification. Confirmed a visible macOS emulator window, Android main screen, empty crash buffer, and successful second-run reuse. No application rebuild, large download, application-source edit or macOS reference change. See [launcher diagnosis and command](LAUNCH.md).

## Premium minimal UI — 29 October 2026

Completed the focused Compose redesign: original macOS icon copied unchanged; compact branded header; Material navigation drawer; charcoal/neutral Light and Dark themes with restrained red; promotional cards/subtitles removed; consistent rows, mini player and original-logo vinyl. Saved navigation/search/list state and playback continuity verified. Six unit tests, seven existing playback/data Android regressions and two drawer/navigation Android tests passed; lint has zero errors. 320dp / 1.3× text, landscape controls and real vinyl pause/resume screenshot checks passed. Updated APK installed/launched on Mango36 with the existing launcher. No playback/data/launcher source changes or macOS modifications. See [redesign report and screenshots](REDESIGN.md).

## Vinyl center and YouTube investigation — 29 October 2026

Removed the vinyl logo’s translated offset and aligned the original image to the disc pivot. Six unit tests, ten Android alignment/navigation/playback/data checks, and lint passed. Real Mango36 screenshots verify centering during rotation, pause/resume and compact resizing. Regression/demo playback used a separate temporary app sandbox; before/after checks confirm the existing library rows and preferences were preserved. The normal debug APK was installed/launched through the unchanged launcher. No existing downloads were removed and the macOS project stayed read-only. [Alignment evidence](VINYL_ALIGNMENT.md).

YouTube’s supported APIs were checked against current official documentation. No extraction or hidden audio playback was implemented. In-app official search requires a Data API key; compliant playback requires a visible embedded video experience. Implementation and real search/playback validation await the user’s requested approval for this material playback change and provision of a key. No paid API, subscription or Cloud project was introduced. [Reviewable proposal](YOUTUBE_OPTIONS.md).

## Audio-only integration research — 29 October 2026

The user rejected visible YouTube video playback and requested research before any implementation. Inspected the macOS yt-dlp search/HLS resolver/mpv flow read-only and evaluated on-device yt-dlp/Python, JVM extractors, backend resolution and rights-cleared native-audio providers. Android can technically retain Media3 and the current UI; unofficial extraction does not confer provider authorization and introduces significant compatibility maintenance. Documented current Python/JS packaging constraints, HLS-module gap, service-level queue resolution, licensing/distribution issues, and an approval-gated provider-adapter recommendation in [AUDIO_ONLY_OPTIONS.md](AUDIO_ONLY_OPTIONS.md). No code, runtime, APK, user data, macOS project or playback changes; no YouTube API key requested. Awaiting approval before online streaming implementation.

## Approved on-device online audio — 29 October 2026

Implemented separate Library/Online search, real yt-dlp metadata and thumbnails, embedded ARM64 Python/QuickJS runtime, service-owned just-in-time audio resolution and Media3 HLS playback. Existing player, original-logo vinyl, drawer, local imports/downloads and Room records are retained. Stable metadata, favorites and history persist; temporary audio URLs remain in memory. Offline Mode blocks online operations and YouTube downloads are unavailable. Runtime provenance and licenses are included. Final verification and installation evidence: [ONLINE_AUDIO.md](ONLINE_AUDIO.md).

Final result: normal APK built, installed in place and launched on existing Mango36; installed checksum matches the artifact. Six unit tests and 16 distinct Android checks passed across the full regression and targeted navigation rerun; lint has zero errors (29 warnings across modules). Actual online music, background/system media controls, queue advancement/cache, offline blocking and existing local features were exercised. Original Room records/preferences survived migration. Physical listening/routing/lock-screen and extended provider/network reliability testing remain. See [final evidence and instructions](ONLINE_AUDIO.md#final-results--mango36).

## Stable local Git checkpoint — 29 October 2026

Prepared `android-online-audio-working-v1` on local `main`; the workspace initially had no Git repository and the supplied GitHub repository had no refs. Source/APK backup was created before Git setup. All 16 Android checks passed together in a fresh full run; six unit tests passed freshly, the normal build passed, and lint has zero errors. Rebuilt APK matches the preserved working APK byte-for-byte. No implementation files changed. Ignore rules protect generated output, local configuration and credentials. See [checkpoint scope and recovery location](CHECKPOINT.md). No push was performed.

## First-pass audio crackling investigation — 29 October 2026

**Unresolved; not confirmed fixed.** User confirmed playback/controls functional but intermittent crackling remained after normal app restoration. Added bounded debug player/format/decoder/network/actual AudioTrack-underrun diagnostics and an opt-in non-mutating PCM comparison probe. Two permitted songs completed full first and natural Repeat One passes twice without seeking. No starvation, underruns, clipping-threshold samples or playback errors were observed; small PCM differences do not establish their connection to the audible issue. Corrected the long comparison harness to use normal ActivityScenario timing; stopped the test process and restored interactive normal Mango Music. No speculative bitrate, gain, buffer or decoder changes were applied.

Six fresh unit tests, 18 regular Android checks, the separate full-pass comparisons and lint/build passed (zero lint errors). Diagnostic APK installed/launched on Mango36; user library/preferences, original recovery tag and backup hashes preserved. No commit/push or macOS modifications. Actual listening synchronized with the new diagnostics and physical-device comparison remain necessary. [Investigation, measurements and exact APK](AUDIO_CRACKLE_INVESTIGATION.md).

## Mix / continuous autoplay — 29 October 2026

Implemented optional, persisted Mix controls in Online search, Up Next and Now Playing. Service-owned bounded discovery prefers actual YouTube radio metadata, with public artist/title music-search fallback. Batch refill, duplicate/recent-song filtering (including obvious alternate uploader credits), manual priority under Shuffle, cancellation, unavailable auto-track recovery, retry/backoff and late-result continuation are implemented. Repeat One/All suspend discovery; Offline Mode blocks it. Original player/resolver, local library/downloads, Room schema, branding and vinyl are preserved.

Sixteen unit tests and 27 distinct Android checks passed across the full regression and final targeted runs; lint has zero errors. Actual radio and fallback results plus recommended-song native background playback after a manual track were verified on Mango36. Updated debug APK installed/launched, checksum verified, existing library/preferences and recovery tag/backups preserved. No commit/push or macOS modifications. Crackling investigation remains paused with findings retained for physical-device testing. [Behavior, screenshots, measurements and APK](MIX_AUTOPLAY.md).

## Mix checkpoint and authorized downloads — 29 October 2026

Phase 1 complete: the user-confirmed working Mix implementation was backed up, built/tested, committed as `3f2e676bbd48c0fdbde5f26384dd26d3252418c1`, tagged `android-mix-working-v1`, atomically pushed and verified against GitHub before download changes. The original online-audio tag and backups remain intact. [Checkpoint](MIX_CHECKPOINT.md).

Phase 2 implementation: independent WorkManager/yt-dlp downloads for user-confirmed permitted YouTube content and eligible HTTPS audio, progress/cancel/retry, additive Room persistence, private verified files/artwork, local-first playback, duplicate protection and safe deletion/storage display. Five focused emulator tests passed including an actual YouTube download, offline local playback with network disabled and zero resolver calls, and ongoing online playback during download. Final: 32 Android regressions passed (1 long crackling experiment skipped), 20 unit tests passed, build/lint passed with zero lint errors. Updated normal APK installed/launched on Mango36; all 23 original library rows/fields and preferences preserved. A real UI search-result download, artwork and favorite survived an actual validation-app process restart; local playback resumed when selected afterward. Audio streaming settings and Mix logic are unchanged. Downloads changes remain uncommitted for review. [Implementation, restrictions and verification](DOWNLOADS.md).


## Authorized Downloads checkpoint and confirmation label — 30 October 2026

Changed only the positive offline-save confirmation label to **Download**, retaining the exact title, permission explanation, Cancel action and handler. Updated the corresponding UI test. Six focused Android download/UI/migration checks and 20 unit tests passed; build/lint passed with zero errors. Updated normal APK installed/launched on Mango36 with checksum verification. The user authorized committing the completed Downloads implementation and this label change, publishing `main` and the new `android-downloads-working-v1` checkpoint while preserving previous checkpoints/backups. [Checkpoint details and validation](DOWNLOADS.md#authorized-downloads-checkpoint--30-october-2026).
