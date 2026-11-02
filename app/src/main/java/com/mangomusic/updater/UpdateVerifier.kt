package com.mangomusic.updater

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.jar.JarFile

class UpdateVerifier(private val context: Context) {

    fun verifyDownloadedApk(
        apkFile: File,
        currentVersionCode: Long,
        expectedSha256: String? = null
    ): ApkVerificationResult {
        // 1. File existence and size check
        if (!apkFile.exists() || apkFile.length() <= 0) {
            apkFile.delete()
            return ApkVerificationResult(false, "Downloaded update file is missing or empty.")
        }

        // 2. SHA-256 digest verification if available in release metadata
        if (!expectedSha256.isNullOrBlank()) {
            val fileSha256 = calculateFileSha256(apkFile)
            val cleanExpected = expectedSha256.removePrefix("sha256:").trim().lowercase()
            if (fileSha256 != cleanExpected) {
                apkFile.delete()
                return ApkVerificationResult(
                    false,
                    "SHA-256 checksum mismatch (expected: $cleanExpected, actual: $fileSha256). File may be corrupted or modified."
                )
            }
        }

        // 3. Inspect APK archive using Android PackageManager
        val pm = context.packageManager
        val archiveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of((PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES).toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
                )
            }
        } catch (e: Exception) {
            apkFile.delete()
            return ApkVerificationResult(false, "Failed to inspect APK archive: ${e.localizedMessage}")
        }

        if (archiveInfo == null) {
            apkFile.delete()
            return ApkVerificationResult(false, "Downloaded file is not a valid Android APK archive.")
        }

        // 4. Verify package name matches exactly
        if (archiveInfo.packageName != context.packageName) {
            apkFile.delete()
            return ApkVerificationResult(
                false,
                "Package name mismatch: APK is '${archiveInfo.packageName}', but installed app is '${context.packageName}'."
            )
        }

        // 5. Verify downloaded versionCode is strictly newer than installed versionCode
        val apkVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (apkVersionCode <= currentVersionCode) {
            apkFile.delete()
            return ApkVerificationResult(
                false,
                "APK version code ($apkVersionCode) is not newer than currently installed version ($currentVersionCode)."
            )
        }

        // 6. Verify signing certificate
        val apkCerts = extractApkCertificates(apkFile, archiveInfo)
        if (apkCerts.isEmpty()) {
            apkFile.delete()
            return ApkVerificationResult(false, "APK contains no valid signing certificate.")
        }

        val installedCerts = getInstalledCertificates()
        val matchesInstalled = apkCerts.any { apkCert ->
            installedCerts.any { instCert -> apkCert.equals(instCert, ignoreCase = true) }
        }
        val matchesPinnedRelease = apkCerts.any {
            it.equals(UpdateConfig.PINNED_RELEASE_CERTIFICATE_SHA256, ignoreCase = true)
        }

        val isCurrentAppDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // In-place updates on Android strictly require matching the installed signing certificate.
        // If certificates differ (e.g. Debug build -> Release update), Android OS package installer will reject it.
        if (!matchesInstalled) {
            apkFile.delete()
            return ApkVerificationResult(
                false,
                if (isCurrentAppDebuggable && matchesPinnedRelease) {
                    "Cannot update debug build with release APK directly due to signing certificate difference. Please uninstall the debug build first."
                } else {
                    "APK signing identity does not match the installed application."
                }
            )
        }

        // Non-debug production builds must strictly match the official release certificate pin
        if (!isCurrentAppDebuggable && !matchesPinnedRelease) {
            apkFile.delete()
            return ApkVerificationResult(
                false,
                "Production updates must be signed with the official release key."
            )
        }

        return ApkVerificationResult(
            isValid = true,
            packageName = archiveInfo.packageName,
            versionCode = apkVersionCode,
            versionName = archiveInfo.versionName,
            certificateSha256 = apkCerts.firstOrNull()
        )
    }

    fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractApkCertificates(
        apkFile: File,
        archiveInfo: android.content.pm.PackageInfo
    ): List<String> {
        val certs = mutableListOf<String>()

        // Try extracting via PackageManager signingInfo on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.signingInfo?.let { signingInfo ->
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.forEach { sig ->
                    certs.add(calculateSignatureSha256(sig))
                }
            }
        }

        // Fallback to archiveInfo.signatures on older APIs
        if (certs.isEmpty()) {
            @Suppress("DEPRECATION")
            archiveInfo.signatures?.forEach { sig ->
                certs.add(calculateSignatureSha256(sig))
            }
        }

        // Additional resilient extraction via JarFile to parse signatures from APK directly
        if (certs.isEmpty()) {
            try {
                JarFile(apkFile, true).use { jar ->
                    val manifest = jar.getJarEntry("AndroidManifest.xml")
                    if (manifest != null) {
                        jar.getInputStream(manifest).use { stream ->
                            val buf = ByteArray(8192)
                            while (stream.read(buf) != -1) { /* drain to read signatures */ }
                        }
                        manifest.certificates?.forEach { cert ->
                            val md = MessageDigest.getInstance("SHA-256")
                            certs.add(md.digest(cert.encoded).joinToString("") { "%02x".format(it) })
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return certs.distinct()
    }

    private fun getInstalledCertificates(): List<String> {
        val pm = context.packageManager
        val certs = mutableListOf<String>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                }
                pInfo.signingInfo?.let { info ->
                    val signatures = if (info.hasMultipleSigners()) {
                        info.apkContentsSigners
                    } else {
                        info.signingCertificateHistory
                    }
                    signatures?.forEach { sig ->
                        certs.add(calculateSignatureSha256(sig))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val pInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pInfo.signatures?.forEach { sig ->
                    certs.add(calculateSignatureSha256(sig))
                }
            }
        } catch (_: Exception) {}

        return certs.distinct()
    }

    private fun calculateSignatureSha256(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
