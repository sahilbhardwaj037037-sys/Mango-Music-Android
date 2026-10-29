# Playback and provider integrations

Historical research: the user subsequently approved the private on-device yt-dlp experiment. See [current implementation and validation](ONLINE_AUDIO.md). The visible-video proposal remains rejected.

## Working design
- Media3 ExoPlayer plays bundled WAV, imported Android audio files and direct HTTPS audio links supported by Android/Media3 codecs. No desktop executables are bundled.
- Search filters title and artist within the local library (including added streams). It does not search YouTube, Apple Music or Spotify.
- Three 24-second demo loops were synthesized for this project by `scripts/generate_samples.py`. They are original test content, not commercial tracks or provider previews.
- User-selected files are copied into private app storage (200 MB limit). Metadata is read with Android MediaMetadataRetriever. No broad media/storage permission is needed.
- DownloadManager handles explicitly authorized direct HTTPS files. Jobs persist across app process termination; Room stores job IDs, successful local paths and errors. Audio metadata is validated before showing a completed download. Downloads need network access and may incur mobile data usage.
- Offline Mode stops the current queue and filters playback to available local media. Demos are bundled and always available offline.

## Provider investigation
- macOS `YouTubeSearchService`, `AudioStreamResolver`, `AudioDownloadService` and `PlaybackEngine` depend on subprocesses (yt-dlp/mpv). They cannot be copied to native Android.
- [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies) restrict separating audio, background players and unauthorized downloads. Official YouTube Data API search also needs a project/API key and returns metadata, not an unrestricted audio stream. No extraction/bypass integration is implemented.
- [Apple iTunes Search API terms](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/) restrict previews to promotion and prohibit saving/caching them. An entertainment-only preview catalog is not implemented.
- SoundHelix examples were considered, but its official terms page could not be fetched reliably during setup. Those songs were not bundled or offered for download.
- [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats) depend on container, codec and device decoder capabilities. A direct link can still be unsupported, expired, restricted or unreachable; playback shows a retryable error.
- Google's public ExoPlayer test stream `https://storage.googleapis.com/exoplayer-test-media-0/play.mp3` is used only by an Android integration test. Host HTTP 200 is not treated as proof of Android playback.

## Remaining scope
An authorized commercial catalog/search provider requires selecting its Android SDK/API, obtaining real credentials, reviewing playback and offline entitlements, and testing on Android. Smart related-song autoplay, DRM/subscription catalogs, playlists, queue restoration after process death, history retention controls and download deletion/cancellation UI are not part of this V1.

## Authorized download test media

The download integration test uses [Example.ogg](https://commons.wikimedia.org/wiki/File:Example.ogg), by **JesseW**, under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). The recording is downloaded unchanged for device testing, then its local test copy is deleted. It is not included in the APK or the built-in music catalog. Original URL: `https://upload.wikimedia.org/wikipedia/commons/c/c8/Example.ogg`.
