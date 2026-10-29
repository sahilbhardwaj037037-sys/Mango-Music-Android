# Experimental on-device online audio

29 October 2026. Implemented for the explicitly approved private, noncommercial experiment using content the user has permission to access. This is an unofficial extractor integration, not an official YouTube audio SDK or a grant of provider/content rights. Earlier feasibility documents describe the decision history; this document describes the implementation.

## Architecture

Search screen (Library / Online) → `OnlineRepository` → embedded yt-dlp catalog adapter → stable `youtube:<videoId>` metadata → service-owned `OnlineMediaSource` → just-in-time resolution → existing Media3 ExoPlayer / MediaSession → original native Now Playing and vinyl.

Search debounces for 450 ms. Search work and resolution run off the UI thread. Replacing a query cancels its result delivery and prevents subsequent extractor requests. Each request has a 45-second application timeout, 12-second extractor socket timeout and bounded retries; an already-blocked native/socket operation may take its socket timeout to unwind. No query, resolved URL, credentials, cookies or request headers are logged. Playback is unaffected by new searches.

The custom media source resolves independently of the screen/ViewModel, so automatic queue transitions work in background. It posts source changes on the playback looper, rejects stale source generations, forwards required HTTP headers, follows HTTPS redirects and supports HLS plus compatible M4A/WebM audio. Media3's existing buffer, audio-focus, volume and notification/session behavior remains in place; no extra gain or audio processing is applied.

Concurrent resolution for one ID is shared. Transient URLs live only in memory, for at most three minutes or until 60 seconds before the provider's expiry, with a 24-entry cap. The next track is prefetched. Network/HTTP playback failures invalidate the cached resolution and receive one bounded retry; other errors surface a retry action. Restricted content fails without authentication, region/account workarounds, cookies or alternate-client overrides.

Room v1 → v2 adds only nullable `thumbnailUrl`. Stable IDs, canonical watch-page URLs, title/artist, thumbnail, favorites and successful listening history persist. Expiring audio URLs and headers never enter Room. Online rows cannot be downloaded. Existing local import, authorized direct-link downloads and offline playback remain available. Offline Mode stops the queue as before, cancels active extraction, rejects online search/resolution before starting a request, and hides remote thumbnails. An already-started request cannot be retroactively unsent.

## Pinned runtime and provenance

| Component | Version / purpose |
|---|---|
| Chaquopy | 17.2.0, isolated `:online-runtime` library |
| Python | 3.14; packaged runtime reports 3.14.8 on Mango36 |
| yt-dlp | 2026.10.26, Python API; no host executable |
| yt-dlp-ejs | 0.8.0, bundled solver scripts; remote component fetching disabled |
| certifi | 2026.10.23, CA certificates |
| QuickJS-NG | 0.17.0, ARM64 executable and shared library |
| Media3 | 1.8.0, existing ExoPlayer/session plus HLS module |
| Coil Compose | 2.7.0, result thumbnails |

[Chaquopy's compatibility documentation](https://chaquo.com/chaquopy/doc/current/android.html) supports AGP 7.3–9.4 and ARM64. The project retains AGP 9.0.1, min API 26 and SDK 36. [Selected yt-dlp release](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26), [JavaScript requirements](https://github.com/yt-dlp/yt-dlp/wiki/EJS). Python and QuickJS were actually executed inside Android, rather than assumed compatible from desktop tests.

QuickJS binaries come from the official Termux `quickjs-ng_0.17.0_aarch64.deb`, verified against the SHA-256 in the official package index. Exact source URLs, package and binary checksums are in `online-runtime/runtime-provenance.json`. `python3 scripts/verify-online-runtime.py` verifies the packaged copies. The executable is named `libmangoqjs.so` so Android extracts it into its executable native-library directory; `LD_LIBRARY_PATH` points to that directory for `libqjs.so`. No Termux installation, shell commands, privileged installation or developer-machine runtime is required on the device. QuickJS's license is bundled in assets; Python packages retain their distribution metadata/licenses in the packaged environment. No runtime auto-updates are performed.

The debug APK is approximately 42 MB, up from approximately 23 MB. This build supports **arm64-v8a only**; Intel emulators/32-bit phones need a separately verified JS binary and ABI configuration. Builds require a host Python 3.14 compatible with Chaquopy (already present on this machine). Keep yt-dlp/EJS/Python/JS upgrades coordinated and rerun live Android tests after updating. Provider changes, rate limits and anti-bot checks can break extraction without an app change; restricted results must fail, not trigger bypass attempts.

## Validation

Live-network evidence and final test/install results are recorded below and in `test-evidence/online/`. Regression instrumentation runs in `com.mangomusic.validation`, separate from the existing user's package. No mocks establish online playback success.

Known limits: search supplies uploader/channel as artist when true artist metadata is absent. Search results may include unavailable or unauthorized content; the caller remains responsible for permissions and unsupported items show an error. No sign-in, account library, paid catalog, YouTube downloading, external backend, API key, subscription or visible video player was added. No guarantee of YouTube terms compliance or suitability for distribution is implied by technical success; review provider terms and component licenses before distributing.

Actual Android playback state, advancing position, decoding/output path, pause, seek, background operation and session controls can be tested in the emulator. Audible fidelity, clipping, physical speaker/Bluetooth/headset behavior, hardware lock-screen controls, OEM battery restrictions and real network handover still require physical-device listening/testing. Demo audio assets are unchanged.

## Final results — Mango36

- `assembleDebug` and six JVM domain tests passed. Lint completed with **zero errors**, 28 app warnings and one intentional ARM64/ChromeOS ABI warning in the runtime module. Existing dependency/style/service warnings are retained; no unrelated upgrades were made.
- **16 distinct Android checks passed across the full regression and targeted navigation rerun.** The full run passed all 14 data/playback/online/vinyl checks, including the five real-network online tests. Two navigation checks exposed online records entering the Library list; Library was scoped to original demos/imports/direct links, and both unchanged navigation tests then passed. `test-evidence/online/android-checks.json` records the exact checks and run scope. An earlier favorite assertion was made idempotent for repeated test runs.
- Live query **Kevin MacLeod Carefree** returned ten actual results with titles, channel/artist and thumbnails. `youtube:sMcWOaJFuw0` (Carefree / Kevin MacLeod) played through native Media3. `youtube:aqz-KE-bpKQ` (Blender Foundation test media) independently passed native playback, pause, seek/resume, background progress, media notification, Android system media play/pause keys, and Room favorites/history checks.
- Online next/previous in background, automatic end-of-track advancement, stable metadata/history and memory-cache reuse passed. Offline search/resolution was rejected without increasing the outgoing-operation counter. Real Search UI selection opened the original-logo native player; another live query left the selected track playing. Library/Online are separate; online favorites/history and queue remain accessible through their existing destinations.
- Existing local import, Room reopen/persistence, authorized direct download/local playback, bundled playback, HTTPS playback, transient audio focus, repeat one/all, shuffle, navigation/configuration continuity and vinyl center/rotation/pause/resume/resize checks passed. [Search screenshot](screenshots/online-results.png), [native Now Playing screenshot](screenshots/online-now-playing.png).
- The normal **com.mangomusic** APK was rebuilt, installed with `-r` and launched through the unchanged `scripts/launch-mango.sh` on **Mango36 / emulator-5556 / Android 16 / API 36 / ARM64**. The installed APK SHA-256 matches the final artifact. Temporary validation packages were removed after evidence capture; the normal app remains installed and running.
- Before/after audit confirms all original track columns and preferences are unchanged, v1 → v2 migration succeeded, and all existing thumbnail values start null. No imported private user-content files existed in the audited baseline; Android regenerated its non-content ProfileInstaller marker during installation. Download records/local URI fields are included in the preserved rows, and the migration test separately retained a test download file. Existing downloads were not deleted. No production online test tracks were inserted: online playback tests used the isolated package.

Final APK: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk` — **44,148,995 bytes** (42.1 MiB), SHA-256 `1bb63c922b92441346468e9d904574ac49d157dfc1cf67cc279c9a65684f6baa`. Exact installation, build/lint and user-data evidence is in `test-evidence/online/`.

Reproduce tests without touching the normal library:

```sh
./scripts/gradle-env.sh -I scripts/isolated-tests.gradle :app:connectedDebugAndroidTest
./scripts/gradle-env.sh assembleDebug testDebugUnitTest lintDebug
./scripts/launch-mango.sh
```

Open Search → **Online**, enter a query, then select a permitted result. No API key or new runtime permission is required; existing Internet and foreground-media-service declarations are reused. First resolution can take several seconds, with the existing buffering indicator shown. Restricted/unavailable content produces an error. Long offline/network handovers, naturally expired provider tokens, hostile network failures and every possible unavailable-content condition have not been exhaustively reproduced. The bounded recovery paths are implemented; manual physical-device listening and interruption tests remain necessary. No audible-fidelity, clipping-free output, real headset/Bluetooth or hardware lock-screen validation is claimed.

The macOS project/installed app, original logo, demo WAV assets and emulator launcher were not modified. No commit or push was performed.
