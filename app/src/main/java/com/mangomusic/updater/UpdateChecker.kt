package com.mangomusic.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class UpdateChecker {

    suspend fun checkLatestRelease(
        currentVersionCode: Long,
        endpointUrl: String = UpdateConfig.LATEST_RELEASE_URL
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val uri = try {
            URI(endpointUrl)
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error("Invalid update URL: ${e.localizedMessage}")
        }

        if (uri.scheme?.lowercase() != "https") {
            return@withContext UpdateCheckResult.Error("Insecure update URL scheme: HTTPS is required")
        }

        val host = uri.host?.lowercase()
        if (host !in UpdateConfig.ALLOWED_DOWNLOAD_HOSTS) {
            return@withContext UpdateCheckResult.Error("Untrusted update host: $host")
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MangoMusic-Android-Updater")
            }

            val responseCode = connection.responseCode
            when (responseCode) {
                200 -> {
                    val body = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                        it.readText()
                    }
                    parseReleaseJson(body, currentVersionCode)
                }
                404 -> UpdateCheckResult.Error("No releases found on GitHub repository.")
                403 -> UpdateCheckResult.Error("GitHub API rate limit exceeded. Please try again later.")
                else -> UpdateCheckResult.Error("Failed to check for updates: HTTP $responseCode")
            }
        } catch (e: java.net.UnknownHostException) {
            UpdateCheckResult.Error("No internet connection. Please check your network and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            UpdateCheckResult.Error("Connection timed out while checking for updates.")
        } catch (e: Exception) {
            UpdateCheckResult.Error("Update check failed: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            connection?.disconnect()
        }
    }

    fun parseReleaseJson(jsonString: String, currentVersionCode: Long): UpdateCheckResult {
        return try {
            val root = JSONObject(jsonString)
            val isDraft = root.optBoolean("draft", false)
            if (isDraft) {
                return UpdateCheckResult.Error("Latest release is a draft and cannot be installed.")
            }

            val tag = root.optString("tag_name", "").trim()
            val releaseName = root.optString("name", tag)
            val releaseNotes = root.optString("body", "")
            val publishedAt = root.optString("published_at", "")

            val assetsArray = root.optJSONArray("assets")
            if (assetsArray == null || assetsArray.length() == 0) {
                return UpdateCheckResult.Error("Release contains no downloadable assets.")
            }

            // Find valid APK asset
            var apkUrl = ""
            var apkSize = 0L
            var apkSha256: String? = null

            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val contentType = asset.optString("content_type", "")

                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    val digest = asset.optString("digest", "")
                    if (digest.startsWith("sha256:", ignoreCase = true)) {
                        apkSha256 = digest.substringAfter(":").trim()
                    }
                    break
                }
            }

            if (apkUrl.isBlank()) {
                return UpdateCheckResult.Error("No valid .apk asset found in the latest release.")
            }

            // Fallback SHA-256 detection from release notes body if not in asset metadata
            if (apkSha256.isNullOrBlank()) {
                val shaRegex = Regex("""(?:APK SHA-256|SHA-256|sha256)[:\s`]+([0-9a-fA-F]{64})""", RegexOption.IGNORE_CASE)
                apkSha256 = shaRegex.find(releaseNotes)?.groupValues?.get(1)?.lowercase()
            }

            // Extract Version Code
            val versionCodeRegex = Regex("""versionCode\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            val extractedVersionCode = versionCodeRegex.find(releaseNotes)?.groupValues?.get(1)?.toLongOrNull()
                ?: versionCodeRegex.find(releaseName)?.groupValues?.get(1)?.toLongOrNull()

            // Extract Version Name
            val versionName = when {
                tag.startsWith("android-v", ignoreCase = true) -> tag.substringAfter("android-v")
                tag.startsWith("v", ignoreCase = true) -> tag.substringAfter("v")
                else -> tag.ifBlank { "1.0" }
            }

            val releaseVersionCode = extractedVersionCode ?: parseVersionCodeFromVersionName(versionName)

            val releaseInfo = ReleaseInfo(
                tag = tag,
                versionName = versionName,
                versionCode = releaseVersionCode,
                releaseNotes = releaseNotes.trim(),
                publishedAt = publishedAt,
                apkUrl = apkUrl,
                apkSize = apkSize,
                apkSha256 = apkSha256
            )

            if (releaseVersionCode > currentVersionCode) {
                UpdateCheckResult.UpdateAvailable(releaseInfo)
            } else {
                UpdateCheckResult.UpToDate(versionName, currentVersionCode)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Failed to parse release metadata: ${e.localizedMessage}")
        }
    }

    private fun parseVersionCodeFromVersionName(versionName: String): Long {
        val match = Regex("""(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(versionName) ?: return 1L
        val major = match.groupValues.getOrNull(1)?.toLongOrNull() ?: 1L
        val minor = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
        val patch = match.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
        return major * 10000L + minor * 100L + patch
    }
}
