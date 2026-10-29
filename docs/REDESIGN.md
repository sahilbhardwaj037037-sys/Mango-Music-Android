# Premium minimal UI refinement

Implemented 29 October 2026 in the existing Android app. This is a Compose presentation change, with playback/data architecture preserved.

## Visual identity

Inspected the macOS `AppearanceMode.swift`, `MainWindow.swift`, and `SongRow.swift` read-only. The reference uses semantic surfaces, system typography, compact rows, short headings, and restrained accent/selection color. Android now applies that hierarchy with a charcoal `#121214` background, near-white primary text, neutral secondary gray, and red actions. Light appearance uses pale neutral surfaces and a darker red for readable contrast. System appearance remains supported.

The existing original asset was available at:

`/Users/sahilbhardwaj/Developer/MangoMusic/MangoMusic/Assets.xcassets/AppIcon.appiconset/icon_128x128@2x.png`

It was copied byte-for-byte to `app/src/main/res/drawable-nodpi/mango_logo.png`. No logo was invented, regenerated, recolored, or replaced with a third-party asset. Compose applies a normal rounded icon mask; the original file is unchanged. It appears in the compact header, drawer, vinyl center, and Android launcher icon.

## UI changes

- Small 28dp original logo and 16sp wordmark in a compact app bar; 48dp hamburger/Settings targets.
- Material 3 modal drawer replaces bottom navigation. Search, Up Next, Favorites, History, Downloads, Settings; animated open/close, selected destination, close on selection, gestures and Android Back handling.
- Search is the primary home element. Import Audio and Add Audio Link remain accessible as compact actions that wrap on narrow/large-text layouts. Three demo tracks remain ordinary library rows.
- Removed marketing cards, oversized headings, letter-spaced titles, repeated subtitles and filler empty-state copy. Error, permission/provider explanations and useful empty-state instructions remain.
- Compact consistent rows, neutral separators, restrained current-song selection, favorite/menu controls and a persistent mini player.
- Up Next has one compact page heading and direct queue content. Favorites/History/Downloads share the same hierarchy.
- Now Playing has a flat background, original branded vinyl, prominent playback controls, seeking, shuffle/repeat, favorite/stop and queue access. Rotation math and lifecycle behavior are retained; no album artwork was added.
- Per-destination list positions, selected destination, query, player presentation/angle and dialogs use saved Compose state across navigation/recreation. Search/action headers scroll with content on small screens. Wide windows retain a split library/player layout.
- System-bar icon contrast is reapplied after dialog disposal; closing Settings cannot leave dark icons over the dark background.

## Functional boundary

`MusicViewModel.kt`, `PlaybackService.kt`, `data/Library.kt`, and both emulator launcher files are byte-for-byte unchanged. Media3 ownership/background notifications/focus, Room schema/persistence, queue/repeat/shuffle, downloads/offline, local import, and the launcher were not rewritten. [Hash verification](test-evidence/redesign/unchanged-components.json).

No macOS Mango Music source, Git state, installed application or user data was modified. No commits or pushes were made.

## Verification

- Gradle Wrapper compilation / debug APK build: **passed**.
- JVM unit tests: **6 passed**, including disc angle continuity and search validation. [JUnit](test-evidence/redesign/unit-tests.xml).
- Existing Android playback/data tests: **7 passed** (Room reopen/favorites/history, local import, audio focus, authorized download/local playback, background/notification/repeat/queue controls, HTTPS playback and missing-source error). [Results](test-evidence/redesign/existing-regressions.json).
- Drawer/navigation tests: **2 passed** after adapting the original navigation test to the new UI and adding Back/state recreation/playback continuity coverage. Also checks appearance/offline controls and status-bar contrast after Settings closes. [JUnit](test-evidence/redesign/navigation.xml).
- These nine Android checks passed across the regression and final navigation runs; the data/playback implementation stayed unchanged between them. No tests were removed or skipped.
- Android lint: **0 errors**; remaining advisory warnings include dependency updates, existing KTX suggestions and exported-service/backup configuration guidance. [Report](test-evidence/redesign/lint.txt).
- Installed and launched on the existing **Mango36 Android 16 ARM64 graphical emulator** using the unchanged `./scripts/launch-mango.sh`. No SDK downloads or new emulator instance were needed once Mango36 was started.
- 320dp screen width and 1.3× font scale checked. Import/link actions wrap; song titles/metadata use ellipsis where needed; visible clickable targets were at least 126×126 pixels at 420dpi (**48dp**). [Visual measurements](test-evidence/redesign/visual.json).
- Landscape split view and scrollable playback controls checked. Emulator display/font/orientation overrides restored afterward.
- Vinyl screenshot comparison: 153,969 changed pixels while playing, **zero significant changed pixels while paused**, and 162,652 on resume (renderer noise threshold 5/255). Domain tests separately verify resuming from the saved angle.

## Screenshots

- [Dark library](screenshots/redesign/search-dark.png)
- [Drawer](screenshots/redesign/drawer-dark.png)
- [Light library](screenshots/redesign/search-light.png)
- [Dark player](screenshots/redesign/now-playing-dark.png)
- [Light player](screenshots/redesign/now-playing-light.png)
- [Small screen / larger text](screenshots/redesign/small-screen-large-text.png)
- [Small player controls](screenshots/redesign/small-playback-controls.png)
- [Landscape controls](screenshots/redesign/landscape-controls-light.png)

## APK and remaining scope

Debug APK: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk`.
Current size and SHA-256: [artifact metadata](test-evidence/redesign/apk.json).

No known UI blocker remains. Physical devices and extreme accessibility font scales above the tested 1.3× were not tested. Existing provider/catalog restrictions, session-only queue restoration, and other V1 functional limits remain documented in INTEGRATIONS.md; this UI change adds no provider integration. The app is left open in dark appearance on Mango36, with playback paused.
