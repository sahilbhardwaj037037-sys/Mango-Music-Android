# Mix / continuous autoplay

29 October 2026. Optional Mix runs inside the existing Media3 playback service. Default is off; the choice is saved locally. No video player, new service, API key, paid service, account or YouTube download support was added. The existing experimental provider-access restrictions still apply.

## Controls and queue behavior

- Use **Mix off / Mix on** beside the Online search selector, in Up Next, or beneath the Now Playing controls for online music. Material controls use the current Light/Dark theme and standard touch targets. Up Next displays discovery/loading/error status and **Retry Mix** when needed.
- With Mix on, tapping an online Search result starts from that one seed. With Mix off, the original search-results playback sequence is unchanged. Favorites/History playback and explicit Play All retain their supplied sequences.
- Mix adds up to three recommendations when two or fewer upcoming songs remain. A service-owned coroutine performs catalog work; it never waits in the player/audio thread or blocks a screen. Existing queue contents are not replaced.
- Manually queued tracks go before the pending Mix tail. Play Next has immediate priority. Automatic rows are marked **Mix** in Up Next. Per-entry identifiers and a stable shuffle traversal keep unplayed manual additions ahead of recommendations, even when Media3 initially inserts a new entry before the current shuffle position. Already-traversed history remains available to Previous.
- Turning Mix off cancels discovery and removes only unplayed/upcoming automatic entries. The current track and manually queued songs continue. The current shuffle traversal is preserved while returning future shuffle edits to Media3's standard implementation.
- Repeat One and Repeat All suspend discovery and keep normal native repeating of the current song/queue. Returning to Repeat Off permits Mix expansion again. Shuffle randomizes traversal while retaining manual priority. Next/Previous stay native Media3 actions.
- Pausing never causes a failed automatic song to resume itself. Stop, seed changes, disabling Mix, Offline Mode and service shutdown cancel stale catalog delivery. Offline Mode keeps its existing stop/clear behavior and blocks recommendation requests; local music/imports/downloads continue to work as before. Local-only playback does not seed online recommendations.

## Recommendations

The pinned on-device yt-dlp runtime first asks for public YouTube radio metadata associated with the seed (`watch?v=<id>&list=RD<id>`). Flat metadata extraction is bounded to 20 entries, skips known live/upcoming/restricted entries, and uses no downloads, cookies, authentication or access-restriction workarounds. Radio is allowed at most 12 seconds before falling back.

When radio is unavailable or has no usable candidates, the adapter uses public music search with artist-song and title/artist signals. Search requests are bounded to 15 seconds each, using the existing worker executor and cancellation checks. The UI identifies the successful source as **YouTube radio** or **Related search**; fallback is not presented as an official provider Mix. No playback URLs are requested by recommendation discovery. Automatic tracks resolve just in time through the existing audio resolver, with its existing prefetch, headers and expiry handling.

Filtering excludes IDs already in the queue, the last 50 history entries, up to 100 recent session tracks and up to 100 removed/failed IDs. Normalized artist/title keys exclude obvious alternate uploads of the same recording, stripping common official-video/lyrics/copyright labels and artist prefixes. Artist credits embedded in another uploader’s title are matched in either direction; matching cleaned titles with near-equal known durations are also filtered. Recommendations reject invalid IDs, empty titles and known durations outside 30 seconds–20 minutes. This is metadata deduplication, not an acoustic fingerprint: differently credited uploads or covers may evade it, and provider relevance still requires listening/review.

Immediately before insertion, the controller checks the latest queue again so manual edits made during a request take priority. Stable metadata is saved in the existing Room schema before queue insertion; transient streams/headers are never persisted. Old automatic queue history is trimmed when the queue exceeds 80 entries, retaining a useful Previous window and all manual entries.

Errors and empty results leave current playback and manual entries untouched, with 30/60-second backoff and at most three failed catalog attempts per seed until retry or a new seed. A late successful result can continue an ended queue if playback is still requested. Unavailable **automatic** tracks may yield to the next queued item after the existing bounded playback retry; three consecutive automatic failures stop that recovery loop and expose Retry Mix. Manual playback errors retain the original explicit retry behavior.

Implementation: `MixSettings` shares UI/service state; `MixQueueController` owns cancellation, refill and queue ordering; `MixPolicy` contains pure filtering/order rules; `RecommendationProvider` separates catalog work from player mutations. No Room migration or audio buffering/decoder/bitrate changes were needed.

## Verification

Results and final APK details are recorded below and in `test-evidence/mix/`. Fake-provider tests use original bundled audio only for deterministic queue/error cases; they are not evidence of online playback. Separate live-network tests query actual provider metadata and play a recommendation through the native service in the background after a manually queued local song.

The crackling investigation is paused at the user's request. The user observed crackling in both YouTube and Mango Music inside Mango36 while macOS audio was clean, making the emulator environment the leading suspected cause. [Previous diagnostics](AUDIO_CRACKLE_INVESTIGATION.md) are preserved for physical-device testing. Mix test success does not establish audible fidelity or fix crackling.

Provider implementation checked against the [pinned yt-dlp YouTube tab/radio extractor](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/yt_dlp/extractor/youtube/_tab.py). Provider availability/relevance can change independently of the app; no personalized signed-in radio experience is promised.

## Final results

- **16 fresh unit tests passed:** six existing domain checks, eight Mix filtering/order cases, and two shuffle-clone/Previous-navigation invariants. These cover alternate uploader credits, distinct versions, and randomizing new batches without reordering existing history.
- **27 distinct Android checks passed across the full regression and focused final reruns.** The long crackling comparison was deliberately skipped. Nine final Mix checks cover manual priority/Shuffle traversal, Mix off/on, Repeat One/All suppression, Offline Mode, stale cancellation, unavailable auto tracks, failed/empty catalogs with backoff, late results resuming the first new song, UI navigation/configuration persistence, live provider discovery and actual automatic playback. Existing local import/download/playback, favorites/history, audio focus, navigation and vinyl tests passed. A final live variant also started with Shuffle already enabled, verifying randomized batch insertion, manual priority and native background autoplay in that configuration.
- Real YouTube radio returned 19 entries for the permitted test seed. A deliberately unavailable radio seed exercised the public-search fallback and returned five actual music results. The live service test expanded the queue, played a manually queued local song first under Shuffle, then automatically played a genuine recommended online track in background. Current playback continued when Mix was turned off. See [provider](test-evidence/mix/provider.json), [fallback](test-evidence/mix/fallback.json), [native playback](test-evidence/mix/playback.json), and [combined test scope](test-evidence/mix/test-summary.json).
- `assembleDebug` and lint passed: **zero lint errors**, 29 app warnings and one runtime ABI warning. No dependency/runtime upgrades or audio-quality changes were made.
- Installed and launched the normal `com.mangomusic` APK on existing **Mango36 / emulator-5556 / Android 16 / ARM64** using the unchanged launcher. Installed checksum matches the final APK. The user-library and preferences audit passed; all existing backup checksums and the recovery tag are unchanged. Test evidence was captured separately from the user's application. No commit or push.

[Now Playing screenshot](screenshots/mix-now-playing.png), [Up Next screenshot](screenshots/mix-up-next.png). No artwork replaced the original centered Mango logo.

APK: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk` — **44,196,543 bytes**, SHA-256 `251f8de97c36eb30388196c59d43707bf58555fa14a755a86390e5d8b44e6a5e`.

[Build/APK details](test-evidence/mix/build.json), [data/tag/backup audit](test-evidence/mix/user-data.json). Mix remains off until enabled. For continuous discovery, use **Repeat Off**; the repeat modes intentionally retain their native looping behavior.

Remaining manual verification: recommendation relevance across the user's preferred genres, long sessions/real network handover/provider changes, physical-device notification/headset/lock-screen routes and audible quality. Individual unavailable/restricted items can still occur because flat provider metadata does not guarantee resolvability. Different uploads or covers may evade metadata matching, and common-title/duration matches can conservatively over-filter. Existing Mix queue entries remain session-only, consistent with the existing player architecture. Neither these tests nor Mix implementation establish a crackling fix.

## User-verified Mix checkpoint

The user manually tested this working version and confirmed continuous autoplay works well and audio currently sounds clear. The Mix checkpoint preserves the exact audio/resolver/decoder/buffering behavior of that tested build. Detailed transient traces are excluded from publication; concise diagnostic findings and reproducible tests remain available for future physical-device testing.
