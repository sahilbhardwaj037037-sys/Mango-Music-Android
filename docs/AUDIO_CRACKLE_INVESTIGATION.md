# First-playback crackling investigation

29 October 2026. Recovery tag `android-online-audio-working-v1` and existing backups remain unchanged. No commit or push is part of this investigation.

**Status: unresolved; crackling is not confirmed fixed.** The user confirmed normal playback and controls were restored, but intermittent distortion remained. Successful playback state, progress, UI actions and test assertions do not establish audible fidelity.

## Inspected behavior

The existing resolver chooses compatible audio-only HLS/AAC before progressive M4A/WebM alternatives. Media3 1.8.0 owns playback, native audio focus, background service and media session. Resolver caching holds transient URLs/headers in memory, not downloaded audio. Room holds stable metadata only. There is no audio-file/disk cache to explain a clean second pass.

A natural Repeat One transition reuses the prepared media source, decoder and AudioTrack where compatible. It does not repeat yt-dlp extraction for every loop, and the HLS loader still performs segment requests. In the first baseline, the first pass included 19/26 completed loads for the two songs, versus 10/15 on the repeat, reflecting initial preparation/read-ahead. This alone does not establish why the user hears crackles.

## Diagnostics added

`AudioPlaybackDiagnostics` observes debug builds only. Numeric Logcat records under `MangoAudio` include selection/pass numbers, playing/play intent, playback state, media buffer duration, loading/throughput, decoder name and initialization time, audio format, audio session/track creation and release, track buffer size, Media3 underruns, **actual Android AudioTrack underrun counts**, and sanitized error class/code. Samples occur every five seconds. The in-memory event ring is bounded to 2,000 records.

The debug renderer factory uses the same Media3 default AudioTrack provider and sink configuration; its provider wrapper observes the returned track. Release builds use the normal default sink. Source resolution logs only elapsed time and HLS/progressive transport. No title, query, URI, authentication material, headers or media bytes are logged by production diagnostics.

The opt-in `PcmProbeAudioSink` forwards consumed PCM unchanged. It records one-second numeric levels/fingerprints and compares repeat samples against the first pass. Reference PCM stays only in RAM, is bounded to 180 seconds for one selection, is cleared on selection changes/release, and is never written as audio. The probe is enabled only by the diagnostic instrumentation test, not normal app playback. Two focused Android tests verify partial writes are neither duplicated nor altered and that one-bit differences are measured correctly.

No format priority, bitrate, gain, player/load-control buffer, queue behavior, audio-focus behavior or startup delay has been changed. No unsupported decoder/buffering workaround has been applied because the measurements do not justify one.

## Full natural-pass comparisons

Two permitted Kevin MacLeod tracks were played from the beginning to their natural end, then through a complete Repeat One pass, without seeking. The comparison was completed twice: first with fingerprints, then with direct sample-difference measurements. Original logo, vinyl and ordinary interactive UI remained present in the corrected harness.

| Measurement | Fluffing a Duck | Sneaky Adventure |
|---|---:|---:|
| YouTube ID | `6hBDh3dRvLc` | `RzMQ7Cmpzi4` |
| Catalog duration | 68 s | 74 s |
| Complete first/repeat passes in final comparison | 2 | 2 |
| Compared PCM samples | 5,935,104 | 6,473,728 |
| Samples differing between passes | 26,087 | 18,780 |
| Largest absolute difference, 16-bit sample units | 112 | 4 |
| Samples at/near full scale (absolute value ≥32760) | 0 | 0 |
| Maximum sampled AudioTrack underruns | 0 | 0 |
| Minimum sampled media buffer while playing | 50,076 ms | 25,260 ms |
| Media3 underrun, codec/sink/player/load error events | 0 | 0 |
| Measured cold resolution/start to >200 ms playing | 10,236 ms | 14,539 ms |

Both used `c2.android.aac.decoder`, AAC-LC, 44.1 kHz stereo, 16-bit PCM and a 64,192-byte AudioTrack buffer (about 364 ms). No speed/gain changes, format switches, post-start rebuffer events or concealed AAC error warnings were observed. These are measurements of these test tracks and this environment, not proof that every result or network condition behaves identically. Startup includes on-device extraction/network work; no artificial delay was introduced.

The samples are not perfectly bit-identical. Some windows match exactly and others differ slightly. These differences alone cannot establish their audibility or identify them as the reported crackle. The probe is before Android's sink/mixer/host output, so it cannot certify what comes out of the speakers.

[Aggregate comparison](test-evidence/audio-crackle/comparison-summary.json), detailed numeric events/fingerprints (preserved in the ignored local trace and checkpoint source archive).

## What the evidence supports

- **Network/media-buffer starvation:** not reproduced. Playback had tens of seconds buffered, no post-start rebuffer and no load errors. Increasing startup buffers or adding a long wait has no supporting evidence.
- **AudioTrack starvation:** not reproduced by either Media3 events or Android's own underrun counter. The existing output buffer is already substantial; increasing it blindly would be speculative.
- **Format/decoder:** a stable AAC format, no decoder errors or severe output clipping were observed. Small first/repeat PCM differences exist; their significance remains unconfirmed. No basis yet for lowering bitrate, forcing another format or adding an FFmpeg decoder.
- **Audio-file caching:** not present. URL caching and warmed decoder/source state can differ across passes, but the repeat still loads HLS segments. A disk-cache explanation would be incorrect.
- **Emulator/host output:** remains a candidate, not a proven cause. Android speaker volume was unmuted at 15/15, host output unmuted at 50%, and the emulator had no `-no-audio` flag. Running emulator version is 37.2.12; host default is the built-in two-channel speakers at 44.1 kHz. The Android mixer runs at 48 kHz. No audio-device/sample-rate/global settings were changed.

Google documents [macOS multi-channel emulator distortion fixes](https://developer.android.com/studio/releases/emulator) in 36.6.11 and 37.2.12, but the current version/two-channel configuration does not match that known older issue. [Reported AAC artifacts with concealed decoder errors](https://github.com/androidx/media/issues/311) and [AAC decoder state issues](https://github.com/androidx/media/issues/1193) were reviewed; their characteristic decoder warnings were absent here. These reports are context, not a diagnosis of this project. [AudioTrack underrun semantics](https://developer.android.com/reference/android/media/AudioTrack#getUnderrunCount()), [Media3 1.8 default output-buffer policy](https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioTrackBufferSizeProvider.java).

## Diagnostic test stall and recovery

The first long comparison used a Compose test rule while waiting on wall-clock playback. The screen stayed on the first track and human interaction did not work normally, even though the service trace showed several natural repeats, healthy buffering and zero underruns. This was an invalid interactive comparison setup, not evidence of a playback-engine stall or an audio fix. Its data was preserved privately, and only the temporary validation app was force-stopped. The normal installed app regained the foreground and the user explicitly confirmed playback and all controls worked; the user also confirmed crackling remained.

The harness now uses ordinary `ActivityScenario`, leaves Compose's frame clock alone, waits for a real clickable accessibility node, and counts native service repeat transitions rather than assuming controller callback timing. It opened Now Playing, advanced to the second track and completed both full comparisons. The earlier silence is not recorded as a quality success.

## Reproduce and remaining validation

Normal debug Logcat:

```sh
adb -s emulator-5556 logcat -s MangoAudio:D MangoOnline:D
```

Complete opt-in comparison in a separate application sandbox:

```sh
./scripts/gradle-env.sh -I scripts/isolated-tests.gradle :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mangomusic.AudioRepeatDiagnosticsTest \
  -Pandroid.testInstrumentationRunnerArguments.audioComparison=true
```

Allow about five to six minutes. Do not interact with the queue during this controlled run. It writes only numeric diagnostics/fingerprints inside the validation sandbox. Run `assembleDebug` without the init script afterward to restore the normal APK artifact.

The next decisive check is listening to a known failing track on the instrumented normal app, noting first-pass crackle times and comparing the natural repeat, then repeating the same experiment on a physical Android device. A host-output capture/loopback comparison would help separate Android PCM from emulator/CoreAudio artifacts. No privileged host audio resets, global configuration changes, unapproved downloads or speculative playback-quality changes were performed. **Manual listening remains required; playback functional YES, controls functional YES, audio quality NOT OK per the latest user confirmation.**

## Final build and regression results

- Six unit tests passed freshly. The normal Android regression run reported 19 cases: **18 passed, zero failed**, with the long comparison intentionally skipped unless opted in. That opt-in comparison passed separately twice; the final comparison run also passed both PCM integrity tests.
- `assembleDebug` and lint passed. Lint: zero errors; 28 app warnings and one existing runtime ABI warning.
- Installed/launched the diagnostic build on the existing Mango36 using the unchanged launcher. Installed APK checksum matches the final artifact. Normal drawer/Back interaction was checked outside instrumentation; the validation test process is stopped. The normal app is on Search, ready for a new selection. No automated test is holding its UI clock.
- Before/after installation audit: all existing Room rows and preferences match exactly. Original branding, demo files, UI, resolver format selection, persistence, downloads, queue/modes and launcher source are unchanged. Recovery tag still points to `a5d1194c2caecce04cf8ebdaa9e3777dfbe00eda`; every original backup artifact checksum still verifies. No macOS project or installed application changes, commit or push.
- APK: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk`, 44,160,771 bytes; SHA-256 `ea282dc8ff044925243c99cdbf50a446580e191e1af00b81181e14d674dfcfd7`. This is an **instrumented diagnostic build**, not a verified crackling fix.

[Regression results](test-evidence/audio-crackle/regressions.json), [build/lint/APK](test-evidence/audio-crackle/build.json), [data preservation](test-evidence/audio-crackle/user-data.json).

**No evidence-supported playback-setting fix was identified or applied. Root cause remains unconfirmed.** A synchronized listening reproduction on this normal instrumented build, and comparison with a physical Android device (none connected during this session), are needed to distinguish audible decoder artifacts from downstream emulator/host output. Further test success must not be reported as an audible-quality fix.

## Follow-up status

The user subsequently observed crackling in both YouTube and Mango Music inside Mango36 while macOS playback was clean, making the emulator audio environment the leading suspected cause. Further crackling investigation is paused at the user's request. The findings and diagnostics remain available for future physical-device comparison; no audible-quality fix is claimed.

## Subsequent user listening confirmation

Before the Mix checkpoint, the user manually confirmed that continuous Mix autoplay works well and audio currently sounds clear. Preserve that working behavior. This supersedes the earlier listening status for the current working build without claiming a proven root-cause fix or physical-device validation. No further crackling experiments are part of the Downloads task.
