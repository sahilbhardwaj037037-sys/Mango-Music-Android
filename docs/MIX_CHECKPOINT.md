# User-verified Mix checkpoint

29 October 2026. Recovery tag: `android-mix-working-v1`. The user manually confirmed this exact Mix implementation continuously autoplays correctly and currently sounds clear. Automated tests do not substitute for that listening confirmation.

The previous `android-online-audio-working-v1` tag remains unchanged. The new checkpoint includes the working Mix implementation, required runtime diagnostic observer classes, concise diagnostic findings and relevant regression tests. Removing observer classes would change the tested build; their presence does not enable the opt-in PCM experiment during ordinary playback. Generated SDK/cache/emulator/build files, credentials and detailed transient audio traces are excluded from publication.

Before any checkpoint/source changes, a source archive and independent APK copy were saved in:

`.local/backups/android-mix-working-v1-20261029-232802+0530/`

`SHA256SUMS` and `BACKUP.json` identify scope and hashes. The source archive includes local diagnostic material for future physical-device work, but excludes regenerable SDK/emulator/caches/build output and Git metadata. A verified Git bundle is added after commit/tag creation.

Preserved working APK SHA-256:

`251f8de97c36eb30388196c59d43707bf58555fa14a755a86390e5d8b44e6a5e`

The Mix checkpoint is committed/tagged/pushed before Downloads implementation begins. Downloads changes belong to a later uncommitted working tree for user review. No decoder, audio sink, gain, format priority, buffer configuration or streaming-resolution changes are part of checkpoint preparation. No macOS project/app changes, history rewrite or force-push.

Validation before commit: 27 Android regressions passed with zero failures (the long crackling comparison stayed skipped); 16 unit tests passed freshly; build and lint passed with zero lint errors. The rebuilt normal APK matches the preserved APK byte-for-byte, and all 64 implementation/build/runtime/script files compared against the source archive are unchanged. [Android results](test-evidence/mix/checkpoint-android-tests.json), [source/APK/unit comparison](test-evidence/mix/checkpoint-build.json).
