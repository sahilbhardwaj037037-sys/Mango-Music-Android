# YouTube integration decision — 29 October 2026

Historical research: the user subsequently approved the private on-device yt-dlp experiment. See [current implementation and validation](ONLINE_AUDIO.md). The visible-video proposal remains rejected.

Status: **Historical visible-video proposal — rejected by the user.** Do not implement this proposal or request a YouTube API key. The current audio-only technical/terms assessment is [AUDIO_ONLY_OPTIONS.md](AUDIO_ONLY_OPTIONS.md), and streaming implementation awaits a new approval.

## Reference experience (read-only)

The macOS `YouTubeSearchService.swift` validates the query, invokes `yt-dlp` for ten flat search results, and maps video ID, title, channel/uploader/artist and duration. `AudioStreamResolver.swift` resolves playback separately; the desktop playback stack uses mpv. Android currently uses Media3 for authorized direct audio/local files. The desktop extraction flow was not ported.

## Supported path proposed for approval

1. Add a YouTube search mode inside Mango Music using the official [Data API `search.list`](https://developers.google.com/youtube/data/reference/rest/v3/search/list), `part=snippet`, `type=video`, `videoEmbeddable=true`, with a real user-provided API key. A music-category filter can narrow results but does not establish download/audio-extraction rights.
2. Display returned video titles, uploader/channel labels and available thumbnails. A channel label must not be misrepresented as a verified performing artist.
3. Selecting a result displays the official [IFrame player](https://developers.google.com/youtube/iframe_api_reference) inside an Android OS WebView. Keep the video and YouTube controls/branding unobscured at the documented minimum size. Handle loading, buffering, unavailable/embedding-disabled content, identity/referrer errors and autoplay/user-gesture requirements.
4. Keep local Now Playing/vinyl, native audio queue, favorites/history, downloads and offline library intact. Pause local audio before YouTube playback. YouTube playback must pause when its player is no longer displayed or the app goes to the background. It cannot become a hidden Media3 audio stream behind the vinyl.
5. Do not add YouTube downloading/offline saving, extract audio, bypass restrictions, invent credentials or silently switch providers. Any changes to mixed local/YouTube queues or stored YouTube library entries need a separately reviewable design.

This is a materially different **visible video** experience from the existing native audio player, so the user's requested approval is necessary before implementing it.

## Documented restrictions

[YouTube Developer Policies](https://developers.google.com/youtube/terms/developer-policies) prohibit separating audiovisual components, background/hidden player features, and unauthorized downloading/offline copies. Public YouTube APIs do not provide an unrestricted audio-only URI for ExoPlayer.

[Required Minimum Functionality](https://developers.google.com/youtube/terms/required-minimum-functionality) explicitly supports Android OS WebView/Custom Tabs. An app-hosted player must identify its client with the documented referrer, based on the installed app ID; embedded video/control size and visibility requirements apply. A visual player cannot be covered by the rotating disc.

The [Data API overview](https://developers.google.com/youtube/data/getting-started) requires a Google Cloud project with the API enabled and an API key for applicable public requests. No key is configured in this Android project. No Cloud project, billing, subscription or paid third-party API was created. Search is subject to the user's Google project quota; individual videos remain subject to owner, regional, age and embedding restrictions.

## Information needed

- Approve in-app visible YouTube video playback, with no YouTube background/audio-only/download mode.
- Provide a YouTube Data API v3 key through untracked local configuration, rather than committing it or pasting it into source. Suggested local entry: `YOUTUBE_API_KEY=...` in the existing ignored `local.properties`. This is a proposed input name; code does not read it until the integration is approved and implemented.
- Agree on supported key restrictions for the test/distribution setup before shipping. Client APKs are not a secret store; no credential protection is claimed merely by excluding the local file from Git.

## Verification status

Actual YouTube search results and playback in Mango Music: **not tested / not implemented**. Reading official documentation or playing generic HTTPS MP3 test media is not treated as YouTube verification. After approval/key setup, test real search, selectable results, actual video playback events, buffering/errors, background pausing and switching back to local playback on Mango36.
