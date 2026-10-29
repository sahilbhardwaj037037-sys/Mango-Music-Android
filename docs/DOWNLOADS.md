# Downloads / offline audio — 30 October 2026

The user-approved Mix version was committed and pushed **before** these changes. Recovery commit `3f2e676bbd48c0fdbde5f26384dd26d3252418c1`, annotated tag `android-mix-working-v1`; original online-audio tag remains intact. [Checkpoint and backup details](MIX_CHECKPOINT.md).

## Behavior

Download is available in eligible song menus and Now Playing. Each request asks the user to confirm permission from both the source and content owner. YouTube search results use the existing embedded yt-dlp/Python/QuickJS installation through a **separate download adapter**; explicitly permitted direct HTTPS links are also supported. No API key, backend, account login or subscription was added.

Room schema 3 adds download state, byte counts, work/attempt leases, local artwork, SHA-256 and permission confirmation. Migration is additive and retains favorites, history, track IDs, imports and existing saved-file references. The old Android DownloadManager reconciliation remains for previously queued jobs.

WorkManager runs constrained, persistent, foreground download jobs. A separate background-priority executor serializes file transfers and validation, independent of the streaming resolver executor. Actual byte progress appears in Downloads and notifications, with cancellation and explicit retry. Repeated requests use unique work IDs and completed-file checks. Cancellation invalidates the Room lease; stale callbacks cannot register cancelled or superseded transfers. A pending callback may take up to the network read timeout to unwind.

Files live under the app's persistent internal `files/downloads/media` folder; private UUID staging folders never appear as completed downloads. Verified audio is atomically moved into place before registration. Surviving published files can be recovered by their original WorkManager job after process death. Interrupted partial downloads restart with a newly resolved source, rather than reusing expired URLs. Old abandoned staging directories are reclaimed by subsequent transfers. Errors offer retry; Offline Mode pauses/cancels work until the user retries online. WorkManager can reschedule interrupted jobs when connectivity returns.

Downloads prefer compatible audio-only M4A/WebM or native HLS. Android MediaExtractor verifies an audio track and duration; AAC transport streams are remuxed with MediaMuxer without decoding, changing gain or re-encoding. Original audio samples are retained. A 500 MB per-item cap and 32 MB reserve protect storage. Artwork is best-effort, capped at 5 MB; a failed thumbnail does not invalidate playable audio. Per-file SHA-256, size and measured duration are saved with existing title/artist/source metadata.

Playback selects a nonempty existing local file first, including canonical YouTube items arriving through queue/Mix transitions. Explicit local YouTube media items bypass the online source entirely. Playback decoder, gain, sink, bitrate choice, buffering, streaming resolver, Mix policy and launcher configuration are unchanged.

Downloads shows saved usage and free app storage. Remove download requires confirmation and only deletes the track's owned download files (or the precise legacy DownloadManager file). It clears download fields while retaining the library row, source, favorite flag and listening history. Imported/original/unrelated files are protected. Android scoped internal storage needs no broad media/storage permission. Notification permission is requested on Android 13+; denying it does not prevent in-app progress.

## Source restrictions and maintenance

This is an unofficial yt-dlp integration, **not YouTube's supported download API**. A technically accessible URL does not grant permission. YouTube's terms impose separate restrictions on downloading and automated access; content-owner permission alone may be insufficient. The confirmation is a user assertion, not automated verification of rights or provider authorization. Use only where all applicable permissions allow it. [YouTube terms](https://www.youtube.com/static?template=terms).

Private, paid/member-only, sign-in/age/region restricted, DRM-marked and explicitly download-disabled sources fail without a workaround. Live/upcoming/video-only or incompatible audio fails clearly. No cookies, credentials, client impersonation overrides, authentication workarounds, remote scripts, ffmpeg downloads or DRM bypass were added. Provider-side behavior may change and require reviewed runtime updates; source permission checks cannot guarantee availability.

WorkManager is Android's persistent scheduling mechanism, but Android 16 job quotas and OEM battery policies can delay long jobs. No promise of unlimited background downloading is made. [Android long-running work documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Validation

Focused run: all five initial download/migration tests passed on Mango36, using the isolated `com.mangomusic.validation` package. Actual Blender Foundation *Big Buck Bunny* YouTube audio downloaded (30,767,611 bytes; measured 634,624 ms) and played/seeked after both emulator Wi-Fi and mobile data were disabled and active network became null. Online resolver request delta was zero. Independent direct online playback remained active throughout the download. Direct HTTPS completion, duplicate prevention, cancel/retry, stale-lease rejection, database reopen, protected unrelated files, favorite/history retention on deletion, and the version-1-to-3 migration passed. Networking was restored in `finally`.

Full regression/build/install results are recorded below. Automated player state and position checks establish functionality; they cannot establish perceived sound quality. Physical-device listening, Bluetooth/headset routing, long/OEM-constrained jobs, and killing the process in every transfer/publication phase remain manual stress checks. The emulator crackling investigation remains paused; no speculative audio adjustments were made.


## Final results — Mango36, 30 October 2026

- Full isolated Android suite: **32 passed, 1 deliberately skipped, zero failures/errors**. XML enumerates 33 tests; the Gradle console's final count includes an extra report event. The skipped test is the paused long audio-crackling listening experiment. Includes real YouTube playback, Mix discovery/priority/background transitions, queue/repeat/shuffle, focus, imported/demo/HTTPS audio, original navigation/vinyl checks, download UI and persistence checks.
- **20 unit tests passed**, normal `assembleDebug` passed, lint **zero errors / 37 warnings**. Runtime native binary hashes and `git diff --check` passed.
- Extra UI check: searched Big Buck Bunny, downloaded the official Blender result `youtube:YE7VzlLtp-4`, confirmed real permission-dialog/notification flow, saved artwork and 28,921,366-byte audio (596,544 ms), then force-stopped/reopened the validation app. Download metadata, favorite, audio bytes/SHA-256 and artwork were identical. Local native playback worked after reopening. This fixture exists only in the validation package.
- Existing normal application migrated in place from Room 2 to 3. All **23 original library rows**, every original column value and all existing preference files matched the private before-update snapshot. No user download was deleted.
- Normal debug APK installed with `-r`, launched by unchanged `scripts/launch-mango.sh`; the graphical Mango36 was restarted when it disconnected. Normal app returned to foreground after isolated UI checks.
- Mix/source/sink/diagnostic renderer/streaming-adapter/launcher implementations compared byte-for-byte against the recovery tag where unchanged. Both recovery tags are preserved; source archive, working Mix APK and Git bundle checksums all reverified. Phase-2 Downloads changes were initially left uncommitted for review; the user subsequently authorized the Downloads checkpoint described below.

APK: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `1de95e5d2dada865a902ca00ba1cbcb4abd9184466b144d64087cac3e43bd23f`

Evidence: [regressions/build](test-evidence/downloads/regression.json), [actual offline transfer/playback](test-evidence/downloads/actual-download.json), [user-data preservation](test-evidence/downloads/preservation.json), [actual process restart](test-evidence/downloads/restart.json). Private raw logs/snapshots are under ignored `.local/downloads`; they are not publication artifacts.

Manual checks still required: audible smoothness/quality while downloading on a physical device, actual lock-screen/routing behavior on that device, prolonged jobs under quota/battery restrictions, and process death during each partially completed transfer/remux/publication stage. Completed-file restart, notification/media-session availability, local seek and concurrent online player continuity were actually exercised; no listening-quality fix is claimed.


## Authorized Downloads checkpoint — 30 October 2026

The positive confirmation label is now simply **Download**. The title, full source/content-owner permission explanation, Cancel action, confirmation handler, and all download/playback/Mix/audio/database code are unchanged from the preceding validated working implementation. The matching UI assertion was updated.

Fresh validation after this label change: six focused Android UI/download/migration tests passed, including real YouTube download, offline network-disabled playback, duplicate/cancel/retry/persistence/safe-deletion checks; 20 unit tests passed; normal build and lint passed (zero errors, 37 warnings). The new normal APK was installed in place and launched on Mango36, with its installed checksum verified. The preceding full 32-pass regression evidence remains linked above; no engine changes were made after that run.

Current APK SHA-256: `e3a3405677ca83e8b9fe70c3c7b47c6f8ece80bed73bf496a103746ecf5f3f3b`

A new, separate source/APK recovery copy is stored at `/Users/sahilbhardwaj/Developer/MangoMusic-Android/.local/backups/android-downloads-working-v1-20261030-021704+0530`. Source/APK/bundle hashes are recorded in its `SHA256SUMS`. Earlier backups and both previous tags remain intact.

The user authorized committing and publishing the complete Downloads implementation with this label change as the annotated recovery tag [`android-downloads-working-v1`](https://github.com/sahilbhardwaj037037-sys/Mango-Music-Android/tree/android-downloads-working-v1). No force-push or history replacement is permitted. [Fresh checkpoint validation](test-evidence/downloads/checkpoint-validation.json).
