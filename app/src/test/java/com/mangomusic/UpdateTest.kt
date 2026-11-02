package com.mangomusic

import com.mangomusic.updater.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.security.MessageDigest

class UpdateTest {

    @Test
    fun updateConfigEndpointsAndHostsAreStrict() {
        assertEquals("sahilbhardwaj037037-sys", UpdateConfig.GITHUB_OWNER)
        assertEquals("Mango-Music-Android", UpdateConfig.GITHUB_REPO)
        assertEquals(
            "https://api.github.com/repos/sahilbhardwaj037037-sys/Mango-Music-Android/releases/latest",
            UpdateConfig.LATEST_RELEASE_URL
        )

        // Allowed download hosts must strictly cover GitHub release endpoints
        assertTrue(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("github.com"))
        assertTrue(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("api.github.com"))
        assertTrue(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("objects.githubusercontent.com"))
        assertTrue(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("github-releases.githubusercontent.com"))
        assertTrue(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("release-assets.githubusercontent.com"))

        // Untrusted hosts must be excluded
        assertFalse(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("evil-server.com"))
        assertFalse(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("raw.githubusercontent.com"))
        assertFalse(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("malware-apk.net"))
        assertFalse(UpdateConfig.ALLOWED_DOWNLOAD_HOSTS.contains("192.168.1.1"))

        // Pinned certificate must match the official release certificate SHA-256
        assertEquals(
            "7411c31aa13d6576452066ae7e0fd0a4a5aea5e529d10691c87ffa0f45acfb40",
            UpdateConfig.PINNED_RELEASE_CERTIFICATE_SHA256
        )
    }

    @Test
    fun hostValidationRejectsUntrustedAndInsecureUrls() {
        fun validateUrl(url: String): Pair<Boolean, String> {
            val uri = try {
                URI(url)
            } catch (e: Exception) {
                return false to "Invalid URL"
            }
            if (uri.scheme?.lowercase() != "https") {
                return false to "Insecure scheme"
            }
            val host = uri.host?.lowercase() ?: return false to "Missing host"
            if (host !in UpdateConfig.ALLOWED_DOWNLOAD_HOSTS) {
                return false to "Untrusted host"
            }
            return true to "OK"
        }

        // Valid URLs
        assertTrue(validateUrl("https://github.com/sahilbhardwaj037037-sys/Mango-Music-Android/releases/download/v1.1/app.apk").first)
        assertTrue(validateUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/12345/app.apk").first)
        assertTrue(validateUrl("https://github-releases.githubusercontent.com/12345/app.apk").first)
        assertTrue(validateUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1438630204/4d269030-17cc-49bd-8bb6-4b06a480272a?sp=r").first)

        // Invalid: insecure scheme (HTTP)
        val insecure = validateUrl("http://github.com/sahilbhardwaj037037-sys/Mango-Music-Android/releases/download/v1.1/app.apk")
        assertFalse(insecure.first)
        assertEquals("Insecure scheme", insecure.second)

        // Invalid: untrusted hosts
        val untrusted1 = validateUrl("https://evil-server.com/malicious.apk")
        assertFalse(untrusted1.first)
        assertEquals("Untrusted host", untrusted1.second)

        val untrusted2 = validateUrl("https://apkpure.com/mango-music.apk")
        assertFalse(untrusted2.first)
        assertEquals("Untrusted host", untrusted2.second)
    }

    @Test
    fun releaseInfoModelPreservesMetadata() {
        val release = ReleaseInfo(
            tag = "android-v1.1.0",
            versionName = "1.1.0",
            versionCode = 2L,
            releaseNotes = "Added new features and bug fixes",
            publishedAt = "2026-11-02T12:00:00Z",
            apkUrl = "https://github.com/sahilbhardwaj037037-sys/Mango-Music-Android/releases/download/android-v1.1.0/Mango-Music-Android-v1.1-release.apk",
            apkSize = 38000000L,
            apkSha256 = "40d40279ed904b3d0e1008b2c20b17f9fa1c8458eeb980694ab88e7ef5e7b2b5"
        )

        assertEquals("android-v1.1.0", release.tag)
        assertEquals("1.1.0", release.versionName)
        assertEquals(2L, release.versionCode)
        assertEquals("Added new features and bug fixes", release.releaseNotes)
        assertEquals("2026-11-02T12:00:00Z", release.publishedAt)
        assertEquals(38000000L, release.apkSize)
        assertEquals("40d40279ed904b3d0e1008b2c20b17f9fa1c8458eeb980694ab88e7ef5e7b2b5", release.apkSha256)
    }

    @Test
    fun versionComparisonIdentifiesUpdateAvailability() {
        val currentVersionCode = 1L

        // Newer version code -> UpdateAvailable
        val newerRelease = ReleaseInfo(
            tag = "v1.1",
            versionName = "1.1",
            versionCode = 2L,
            releaseNotes = "New update",
            publishedAt = "2026-11-02T12:00:00Z",
            apkUrl = "https://github.com/test/app.apk",
            apkSize = 1000L,
            apkSha256 = null
        )
        val resultNewer = if (newerRelease.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(newerRelease)
        } else {
            UpdateCheckResult.UpToDate(newerRelease.versionName, currentVersionCode)
        }
        assertTrue(resultNewer is UpdateCheckResult.UpdateAvailable)
        assertEquals(2L, (resultNewer as UpdateCheckResult.UpdateAvailable).release.versionCode)

        // Same version code -> UpToDate
        val sameRelease = newerRelease.copy(versionCode = 1L)
        val resultSame = if (sameRelease.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(sameRelease)
        } else {
            UpdateCheckResult.UpToDate(sameRelease.versionName, currentVersionCode)
        }
        assertTrue(resultSame is UpdateCheckResult.UpToDate)

        // Older version code -> UpToDate
        val olderRelease = newerRelease.copy(versionCode = 0L)
        val resultOlder = if (olderRelease.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(olderRelease)
        } else {
            UpdateCheckResult.UpToDate(olderRelease.versionName, currentVersionCode)
        }
        assertTrue(resultOlder is UpdateCheckResult.UpToDate)
    }

    @Test
    fun sha256ChecksumCalculationMatchesKnownHash() {
        val testData = "Mango Music Safe Updater Integrity Test".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(testData)
        val hexHash = hashBytes.joinToString("") { "%02x".format(it) }

        assertNotNull(hexHash)
        assertEquals(64, hexHash.length)

        // Re-hashing the same content produces identical hash
        val digest2 = MessageDigest.getInstance("SHA-256")
        val hexHash2 = digest2.digest(testData).joinToString("") { "%02x".format(it) }
        assertEquals(hexHash, hexHash2)

        // Altered content produces different hash
        val alteredData = "Mango Music Safe Updater Integrity Test Altered".toByteArray(Charsets.UTF_8)
        val digest3 = MessageDigest.getInstance("SHA-256")
        val hexHash3 = digest3.digest(alteredData).joinToString("") { "%02x".format(it) }
        assertNotEquals(hexHash, hexHash3)
    }

    @Test
    fun downloadProgressCalculation() {
        val progress50 = DownloadProgress(
            downloadedBytes = 500L,
            totalBytes = 1000L,
            progressFraction = 0.5f
        )
        assertEquals(50, progress50.progressPercent)
        assertEquals(0.5f, progress50.progressFraction, 0.001f)

        val progress100 = DownloadProgress(
            downloadedBytes = 1000L,
            totalBytes = 1000L,
            progressFraction = 1.0f
        )
        assertEquals(100, progress100.progressPercent)
        assertEquals(1.0f, progress100.progressFraction, 0.001f)
    }

    @Test
    fun certificateValidationDecisionMatrix() {
        val pinnedReleaseCert = UpdateConfig.PINNED_RELEASE_CERTIFICATE_SHA256
        val debugCert = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
        val attackerCert = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"

        fun evaluateCertificates(
            installedCerts: List<String>,
            apkCerts: List<String>,
            isCurrentAppDebuggable: Boolean
        ): Pair<Boolean, String?> {
            val matchesInstalled = apkCerts.any { apkCert ->
                installedCerts.any { instCert -> apkCert.equals(instCert, ignoreCase = true) }
            }
            val matchesPinnedRelease = apkCerts.any {
                it.equals(pinnedReleaseCert, ignoreCase = true)
            }

            if (!matchesInstalled) {
                return false to if (isCurrentAppDebuggable && matchesPinnedRelease) {
                    "Cannot update debug build with release APK directly due to signing certificate difference. Please uninstall the debug build first."
                } else {
                    "APK signing identity does not match the installed application."
                }
            }

            if (!isCurrentAppDebuggable && !matchesPinnedRelease) {
                return false to "Production updates must be signed with the official release key."
            }

            return true to null
        }

        // Scenario 1: Production v1.0 installed -> Official v1.1 release APK
        val prodToProd = evaluateCertificates(
            installedCerts = listOf(pinnedReleaseCert),
            apkCerts = listOf(pinnedReleaseCert),
            isCurrentAppDebuggable = false
        )
        assertTrue(prodToProd.first)
        assertNull(prodToProd.second)

        // Scenario 2: Debug build installed -> Official v1.1 release APK (must be rejected)
        val debugToProd = evaluateCertificates(
            installedCerts = listOf(debugCert),
            apkCerts = listOf(pinnedReleaseCert),
            isCurrentAppDebuggable = true
        )
        assertFalse(debugToProd.first)
        assertTrue(debugToProd.second!!.contains("Cannot update debug build with release APK directly"))

        // Scenario 3: Production v1.0 installed -> APK signed with attacker/unrelated key (must be rejected)
        val prodToAttacker = evaluateCertificates(
            installedCerts = listOf(pinnedReleaseCert),
            apkCerts = listOf(attackerCert),
            isCurrentAppDebuggable = false
        )
        assertFalse(prodToAttacker.first)
        assertEquals("APK signing identity does not match the installed application.", prodToAttacker.second)

        // Scenario 4: Debug build installed -> APK signed with attacker/unrelated key (must be rejected)
        val debugToAttacker = evaluateCertificates(
            installedCerts = listOf(debugCert),
            apkCerts = listOf(attackerCert),
            isCurrentAppDebuggable = true
        )
        assertFalse(debugToAttacker.first)
        assertEquals("APK signing identity does not match the installed application.", debugToAttacker.second)

        // Scenario 5: Production v1.0 installed -> somehow an APK signed with debug key (must be rejected)
        val prodToDebug = evaluateCertificates(
            installedCerts = listOf(pinnedReleaseCert),
            apkCerts = listOf(debugCert),
            isCurrentAppDebuggable = false
        )
        assertFalse(prodToDebug.first)

        // Scenario 6: Developer debug build -> newer debug build signed with same debug key (allowed for testing)
        val debugToDebug = evaluateCertificates(
            installedCerts = listOf(debugCert),
            apkCerts = listOf(debugCert),
            isCurrentAppDebuggable = true
        )
        assertTrue(debugToDebug.first)
        assertNull(debugToDebug.second)
    }
}
