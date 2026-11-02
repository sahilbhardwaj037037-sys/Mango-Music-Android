package com.mangomusic.updater

object UpdateConfig {
    const val GITHUB_OWNER = "sahilbhardwaj037037-sys"
    const val GITHUB_REPO = "Mango-Music-Android"
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * SHA-256 fingerprint of the official Mango Music release signing certificate.
     * Matches release/signing-certificate.json.
     */
    const val PINNED_RELEASE_CERTIFICATE_SHA256 =
        "7411c31aa13d6576452066ae7e0fd0a4a5aea5e529d10691c87ffa0f45acfb40"

    /**
     * Allowed hosts for GitHub release assets and redirection.
     */
    val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    /**
     * Minimum interval between automatic background launch checks (6 hours).
     */
    const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    const val NOTIFICATION_CHANNEL_ID = "mango_app_updates"
    const val NOTIFICATION_ID = 2001
}
