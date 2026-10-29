# Online audio working checkpoint

29 October 2026. Local tag: `android-online-audio-working-v1`.

## Repository inspection

The Android workspace initially had no `.git` directory, branch, commit history or remotes. The supplied GitHub repository returned no refs on two read-only `git ls-remote` checks; no published branches/tags or existing checkpoint tag were found. The current implementation therefore had no local commit to compare or push history to inspect.

Initialized this Android workspace on `main` and set `origin` to `https://github.com/sahilbhardwaj037037-sys/Mango-Music-Android.git`. Author identity is configured only in this repository using the exact GitHub noreply address supplied by the user. `user.useConfigOnly=true` prevents guessed identities. No push, force-push, reset, source deletion or macOS project modification was performed.

## Local backup

Before repository initialization and ignore-file changes, created:

`.local/backups/android-online-audio-working-v1-20261029-211909+0530/`

- `project-source-before-checkpoint.tar.gz`: source, assets, bundled ARM64 runtime, wrapper/scripts, documentation and local project/IDE settings.
- `mango-music-working-debug.apk`: independent copy of the original working APK.
- `SHA256SUMS` and `BACKUP.json`: verified archive/APK hashes and scope.

Generated SDK/downloads, emulator data, caches and build directories are excluded from the source archive. These remain untouched in the working project. The APK is preserved separately. A Git bundle of the completed local checkpoint is saved alongside the backup after commit/tag creation.

Original and rebuilt APK SHA-256 both equal:

`1bb63c922b92441346468e9d904574ac49d157dfc1cf67cc279c9a65684f6baa`

All 50 implementation/build/runtime/script files compared against the pre-checkpoint source archive are unchanged. Changes for checkpoint preparation are limited to Git setup, ignore rules and verification documentation.

## Validation and staged-file review

- Full 16-test Android regression passed on Mango36 / Android 16 / API 36 / ARM64, in the separate `com.mangomusic.validation` sandbox. Includes live search, native online playback, background/system media controls, queue transitions, Room migration/persistence, local import/download/playback, navigation and vinyl alignment.
- Six JVM unit tests passed in a fresh task execution.
- Normal `assembleDebug` passed; rebuilt APK is byte-for-byte identical to the preserved working APK. Lint: zero errors, 28 app warnings plus one runtime ABI warning.
- `.gitignore` excludes SDK/caches/emulator data, local properties, generated build output, APK/AAB files, environment files, private keys and signing stores. Curated test logs under documentation are retained deliberately. Bundled runtime binaries and original demo assets are necessary source inputs.
- Reviewed candidate/staged paths and scanned text for common private-key, provider-token, access-key and hard-coded credential patterns without printing values. No credential material was found. No build/cache directories, backup archives, local properties or signing keys are staged.

[Android results](test-evidence/online/checkpoint-android-tests.json), [build, runtime/source comparison and APK results](test-evidence/online/checkpoint-build.json). Existing provider and physical-device limitations remain as documented in [ONLINE_AUDIO.md](ONLINE_AUDIO.md).

This checkpoint is local only. Remote tracking/upstream does not yet exist because GitHub has no published branch. The local commit and tag are ready for an explicitly authorized future push; no remote publication is part of this task.
