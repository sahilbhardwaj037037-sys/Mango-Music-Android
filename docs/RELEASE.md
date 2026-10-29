# Mango Music Android v1.0 signed release

Application ID: **com.mangomusic** (unchanged). Initial **versionCode 1**, **versionName 1.0**. Recovery/release tag: `android-v1.0.0`.

The release contains the already-validated online audio, Mix and Downloads implementation, including the **Download** confirmation button. No playback, resolver, queue, database or UI behavior was rewritten for release. Release is non-debuggable, with debug audio diagnostics disabled by their existing runtime guard. R8/resource shrinking stay disabled for this first release so reflective Python/Java callbacks remain intact. Packaging explicitly includes only ARM64, matching Python/QuickJS; incidental x86 graphics libraries must not advertise unsupported x86 compatibility.

## Signing identity and protected files

A dedicated RSA-3072 PKCS12 key was created once with a 10,000-day certificate lifetime. It is separate from the debug key. Reuse this **exact private key and alias** for every update; never regenerate it when a file/credential is missing. Android updates depend on signing identity. [Android app signing](https://developer.android.com/studio/publish/app-signing).

Public signing-certificate SHA-256:

`7411c31aa13d6576452066ae7e0fd0a4a5aea5e529d10691c87ffa0f45acfb40`

The version-controlled [public certificate pin](../release/signing-certificate.json) contains only public identity metadata. Build tooling compares the keystore, backup and APK against this pin. It refuses new-key generation once the project has a published identity.

Private locations on this Mac:

- Primary: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/.local/signing/mango-music-release.keystore`
- Verified separate local backup: `/Users/sahilbhardwaj/Developer/MangoMusic-Android/.local/signing-backups/20261030-022502+0530/mango-music-release.keystore`
- Private path/alias/hash metadata: `.local/signing/release.json`; backup metadata sits beside the backup key.
- Credential: macOS login Keychain, service `MangoMusic.Android.ReleaseSigning`, account `com.mangomusic`.

Private folders are mode 0700 and key/metadata files mode 0600. The password was generated locally and never printed, embedded in command arguments, committed, or put in Gradle properties. Security.framework accesses Keychain in process memory. `keytool` receives its password through an environment variable; the Gradle wrapper runs without configuration caching and without a persistent daemon. No signing secrets belong in shell command lines, build logs or chat.

Both keystore copies were checked for identical SHA-256 bytes, exported certificate fingerprints, and successful certificate-request signatures with the private key. The Keychain credential unlocked both. `init-key` refuses to overwrite any existing/published identity. Verify the identity any time:

```sh
python3 scripts/release.py verify-identity
```

It prints public fingerprints and paths only. If Keychain is locked, unlock it through macOS; never create another key as a workaround.

## Off-Mac encrypted disaster recovery

**A second folder on this Mac is not disaster recovery.** No external drive was mounted during preparation. The user chose to make the off-Mac copy manually using these export instructions; that copy is not yet verified. Do not assume it exists until you complete these steps.

1. Prepare a separately stored external drive/volume encrypted with APFS encryption in Disk Utility. Keep its unlock passphrase in an independent password manager/recovery record, not solely this Mac's login Keychain. Mount/unlock it locally. Existing drives are not reformatted by these scripts.
2. In Terminal, choose a new folder on that encrypted volume and run (replace `EncryptedBackup` with its actual mounted name):

   ```sh
   python3 scripts/release.py export-recovery \
     --directory "/Volumes/EncryptedBackup/MangoMusicSigning-v1" \
     --confirm-encrypted-volume
   ```

   The flag is your confirmation that the destination is encrypted; the helper does not independently certify drive encryption. It requires a destination under `/Volumes`, refuses an existing destination, and never replaces a backup. It copies the already-encrypted keystore, metadata, and a credential file **inside the encrypted external volume**, without printing credentials. Do not use this command against an unencrypted drive.
3. Verify that the recovery folder exists on the external drive, then eject/lock the encrypted volume and store it separately from the Mac. Consider a second encrypted copy in a separate secure location. Never upload these files to GitHub or attach them to a release.
4. On a replacement Mac, clone the repository, mount/unlock that recovery volume, and restore the **original** identity:

   ```sh
   python3 scripts/release.py restore-keychain \
     --recovery-directory "/Volumes/EncryptedBackup/MangoMusicSigning-v1"
   python3 scripts/release.py verify-identity
   ```

   The helper checks the recovery key against the public certificate pin, creates protected local copies, verifies the credential/private key, and adds the credential to Keychain without printing it. It refuses to replace existing local signing material. Do not display the credential file with `cat`, paste it into chat, or place it in a repository. An old Keychain recovery file alone must not be your only off-Mac recovery method.

## Build and verify

Prerequisites are the existing Studio JDK, project SDK, Gradle wrapper and host Python described in [ONLINE_AUDIO.md](ONLINE_AUDIO.md). No global Gradle/JDK installation or new SDK download was needed. The key and Keychain credential must be available locally.

```sh
python3 scripts/release.py build
python3 scripts/release.py verify-apk
```

This builds `assembleRelease`, runs release unit tests and release lint, verifies the APK signature against the pinned certificate, package/version, non-debuggable status, ARM64-only packaging, 16 KB native LOAD alignment/zip alignment and QuickJS provenance, and copies the distributable to:

`/Users/sahilbhardwaj/Developer/MangoMusic-Android/.local/releases/v1.0/Mango-Music-Android-v1.0-release.apk`

The generated intermediate APK remains under `app/build/outputs/apk/release`; neither APK is tracked. The named distributable is preserved if a different build would overwrite it. Preserve an earlier validated artifact and increment the version for a changed distribution. `.local`, private-key extensions, signing properties, credential exports, backups and APKs are excluded by `.gitignore`.

AGP 9 enables unit tests only for the tested build type by default. The helper sets `-PmangoReleaseTests=true` for release builds/tests. The existing debug workflow and emulator launcher remain available without release credentials.

## Emulator and physical-device testing

Mango36 already has `com.mangomusic` signed with the debug certificate. Installing the release with `adb install -r` failed with **INSTALL_FAILED_UPDATE_INCOMPATIBLE**, as expected. Its original APK was verified unchanged; it was **not uninstalled**, and no data was cleared. Do not try to solve this conflict by silently deleting the user's app/library.

A separate clean project-local **Mango36ReleaseValidation** AVD, Android 16/API 36/ARM64, was created from the same official SDK image. The exact signed distributable was installed there. It has its own data folder under `.local/avd/Mango36ReleaseValidation.avd`; the original AVD data was not copied or replaced. Release regressions explicitly target `emulator-5558` through `ANDROID_SERIAL`.

For repeatable release regressions on that **test-only** AVD:

```sh
ANDROID_SERIAL=emulator-5558 python3 scripts/release.py gradle \
  :app:connectedReleaseAndroidTest -PmangoReleaseTests=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mangomusic.SignedReleaseTest,com.mangomusic.DownloadUiTest,com.mangomusic.DownloadsTest,com.mangomusic.LibraryTest,com.mangomusic.MigrationTest,com.mangomusic.MixPlaybackTest,com.mangomusic.MixTest,com.mangomusic.MixUiTest,com.mangomusic.NavigationTest,com.mangomusic.OnlinePlaybackTest,com.mangomusic.OnlineSearchUiTest,com.mangomusic.PlaybackTest
```

Do **not** run the non-isolated release instrumentation task against the user's original Mango36/phone installation. The two PCM-probe tests require debug-only telemetry and are excluded from release runs. They initially failed by expecting disabled telemetry; no playback fault occurred and no release diagnostic guard was weakened. The direct standalone vinyl alignment harness requires a debug test-host activity and stays in the debug suite; the normal Now Playing/navigation behavior is covered by release activity tests. The long crackling experiment remains paused. Existing full debug regression results are preserved in the Downloads/Mix reports.

On a physical phone, use an **ARM64 device with Android 8.0/API 26 or newer**. Copy the named APK to the phone and open it through the standard Android package installer, allowing installation from the chosen file-transfer/browser app if prompted. Alternatively, select the phone's actual ADB serial and run:

```sh
adb -s PHONE_SERIAL install -r /path/to/Mango-Music-Android-v1.0-release.apk
```

If a debug build with this package is already installed, stop at the signing conflict and arrange data recovery/testing separately. A release-signed version can receive future updates signed by this exact key with a higher versionCode. Physical-device listening, Bluetooth/headset routing, battery-restricted background work, long downloads/Mix sessions and real lock-screen interaction still require manual testing. Automated state/progress checks do not establish perceived sound quality.

## Future versions

1. Preserve this key, certificate pin, alias and backups. Never generate a replacement identity for an update.
2. In `app/build.gradle.kts`, increase `versionCode` for **every distributed update** (initial 1 → next 2), and set the intended `versionName` (for example `1.0.1` or `1.1`). Never reuse an older code or lower it.
3. Build and verify with `python3 scripts/release.py build`. The artifact name/folder follow versionName automatically; signing remains pinned to this key.
4. Rerun release regressions and physical-device update/persistence checks. Preserve the APK checksum and public certificate fingerprint with each release.
5. Review source/ignore rules, commit, create a new annotated version tag, and publish that version's APK separately from Git source. Update the publishing script's explicit tag/name/release notes for the new version; it refuses to overwrite an existing GitHub Release or asset.

The online integration remains unofficial yt-dlp, not a supported YouTube audio/download API. Source/content-owner permission and provider restrictions still apply; no credentials, DRM bypass or access-control workarounds were added for release. [Existing integration restrictions](DOWNLOADS.md#source-restrictions-and-maintenance).


## Final validation — 30 October 2026

- **20 release unit tests passed**; **30 functional signed-release Android tests passed**, with zero failures/skips in the final scoped run. These cover actual on-device Python/QuickJS/yt-dlp search, native/background Media3 playback, Mix recommendations and queue priority, repeat/shuffle/seek/focus, actual downloads while streaming, network-disabled local playback, favorites/history, imports, Room migration, download UI/cancel/retry/duplicates/safe deletion and navigation. The non-debuggable flag, disabled diagnostic observer and executable native runtime were explicitly checked.
- The unchanged debug-only PCM and standalone vinyl harnesses also passed **3 checks** in the existing isolated debug validation app. An initial overly broad release run had 2 failures from PCM tests expecting deliberately disabled debug telemetry; the diagnostic guard was kept intact and the release-only rerun passed all 30 functional checks.
- Signed release assembly, signature/certificate pin, package/version, ARM64-only ABI, 16 KB packaged native LOAD/ZIP alignment, QuickJS binary provenance and `git diff --check` passed. Release lint: **zero errors / 38 warnings**.
- Named distributable, Gradle release output and installed test target matched byte-for-byte. Its release certificate differs from the existing Mango36 debug certificate; the original failed update was non-destructive. No user app was uninstalled or data cleared.
- Source comparison confirms `app/src/main` and `online-runtime` production code match the Downloads checkpoint. Release changes are build/signing/packaging/version configuration, tooling, a release safety test and documentation.
- Both encrypted keystore copies passed checksum, public certificate and private-key signing checks. The actual signing password was absent from all Git publication candidates and the checked Gradle execution-state/log files. Only the public certificate pin is tracked. Existing Mix/Downloads backup hashes were reverified unchanged.

Distributable: `.local/releases/v1.0/Mango-Music-Android-v1.0-release.apk`

Size: **37,543,100 bytes (35.80 MiB)**.

APK SHA-256: `40d40279ed904b3d0e1008b2c20b17f9fa1c8458eeb980694ab88e7ef5e7b2b5`

Signing certificate SHA-256: `7411c31aa13d6576452066ae7e0fd0a4a5aea5e529d10691c87ffa0f45acfb40`

[Machine-readable release validation](test-evidence/release/validation.json). Raw build/device logs are private under ignored `.local/release-validation`. Physical-device/audible-quality and extended OEM/interruption tests remain manual; the user will make the additional encrypted off-Mac recovery export.

The authorized publication uses an annotated `android-v1.0.0` tag on the validated release commit, preserves all older tags/history, and attaches only the named signed APK to the GitHub Release. APK binaries, signing material and backups do not belong in the source commit. [Release publication API](https://docs.github.com/en/rest/releases/releases), [release asset digest API](https://docs.github.com/en/rest/releases/assets).
