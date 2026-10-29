# Vinyl center alignment — 29 October 2026

## Change

The logo had an explicit upward `offset` inside the rotating disc. Rotating that translated image made its center orbit the disc center, explaining the visible upward/rightward drift at different angles.

Removed the offset and explicitly aligned the original image with `Alignment.Center` in the same BoxWithConstraints and graphics-layer rotation as the disc. Full and compact sizes retain their existing logo sizes/mask. The original PNG, animation math, pause/resume angle state and player controls are unchanged. Only the vinyl layout and a test hook/internal visibility were modified in app code.

## Validation

- Gradle Wrapper debug build, **6 unit tests**, and lint (**0 errors**) passed.
- **10 Android tests passed** on the existing Mango36 Android 16 emulator: the existing nine navigation/playback/data checks and a new alignment regression test.
- New test compares rendered disc/logo bounding-box centers within 1 physical pixel at 268dp and 200dp, across 0°, 37°, 123°, 270° and 359°, stationary frames and resumed angle changes.
- Real playback screenshots show rotation, pause at the current angle, resume and compact resizing. Pixel comparison found 159,859 changed pixels during play, zero significant changed pixels while paused, and 141,636 after resume (noise threshold 5/255). Visual inspection confirms the original logo sits at the pivot.
- Tests and demo playback used a **separate `com.mangomusic.validation` sandbox** with the same app/UI implementation, so test favorite/history/download actions did not touch the existing library. The temporary validation app was removed afterward; the normal `com.mangomusic` debug APK was rebuilt and launched with the unchanged launcher.
- Existing app library rows and preferences were compared before/after; see [preservation check](test-evidence/vinyl/user-data.json). No existing downloads were deleted. No macOS reference/application changes, commits or pushes.

[Android JUnit](test-evidence/vinyl/android-tests.xml) · [Unit JUnit](test-evidence/vinyl/unit-tests.xml) · [Visual measurements](test-evidence/vinyl/visual.json) · [APK metadata](test-evidence/vinyl/apk.json)

[Centered paused player](screenshots/vinyl/paused.png) · [Compact player](screenshots/vinyl/compact-paused.png) · [Playing](screenshots/vinyl/playing.png)

## YouTube status

Official integration options were investigated against the macOS search/resolver source read-only. No extraction/downloading or alternative provider was added. Native audio-only YouTube through Media3 is not supported by the public APIs under the reviewed policies. The feasible in-app path is official Data API search plus a visible IFrame/WebView video player, with no hidden/background or offline YouTube playback. This materially different playback mode requires the user's approval and a real Data API key. See [concrete proposal and official sources](YOUTUBE_OPTIONS.md).

Actual YouTube search/results/playback in Mango Music remain **not implemented and not verified** pending that decision and credentials. No paid API, subscription, Cloud project or credentials were introduced. The existing authorized HTTPS/local audio functionality remains tested and available.
