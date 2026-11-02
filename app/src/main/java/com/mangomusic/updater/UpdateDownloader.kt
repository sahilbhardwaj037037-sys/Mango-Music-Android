package com.mangomusic.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

class UpdateDownloader(
    private val context: Context,
    private val verifier: UpdateVerifier = UpdateVerifier(context)
) {

    suspend fun downloadAndVerify(
        release: ReleaseInfo,
        currentVersionCode: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "MangoMusic-update-${release.versionCode}.apk")

        // Clean up any stale partial files
        if (targetFile.exists()) {
            targetFile.delete()
        }

        var currentUrl = release.apkUrl
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        val maxRedirects = 5

        try {
            while (redirectCount < maxRedirects) {
                coroutineContext.ensureActive()

                val uri = try {
                    URI(currentUrl)
                } catch (e: Exception) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid download URL: ${e.localizedMessage}"))
                }

                if (uri.scheme?.lowercase() != "https") {
                    return@withContext Result.failure(SecurityException("Download rejected: Insecure scheme '${uri.scheme}'"))
                }

                val host = uri.host?.lowercase() ?: ""
                val isHostAllowed = host in UpdateConfig.ALLOWED_DOWNLOAD_HOSTS
                if (!isHostAllowed) {
                    return@withContext Result.failure(SecurityException("Download rejected: Untrusted host '$host'"))
                }

                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "MangoMusic-Android-Updater")
                    setRequestProperty("Accept", "application/octet-stream, application/vnd.android.package-archive")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val finalCode = connection?.responseCode ?: -1
            if (finalCode !in 200..299) {
                targetFile.delete()
                return@withContext Result.failure(
                    java.io.IOException("Server returned error code $finalCode during update download.")
                )
            }

            val serverContentLength = connection?.contentLengthLong ?: -1L
            val totalBytes = if (serverContentLength > 0) serverContentLength else release.apkSize

            var downloadedBytes = 0L
            val buffer = ByteArray(32768)
            var lastProgressEmit = 0L

            val inputStream: InputStream = connection?.inputStream
                ?: throw java.io.IOException("No response stream received from update server.")

            FileOutputStream(targetFile).use { fos ->
                inputStream.use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressEmit >= 100 || downloadedBytes == totalBytes) {
                            lastProgressEmit = now
                            val fraction = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else 0f
                            onProgress(DownloadProgress(downloadedBytes, totalBytes, fraction))
                        }
                    }
                    fos.flush()
                }
            }

            // Verify the completed APK file
            val verification = verifier.verifyDownloadedApk(
                apkFile = targetFile,
                currentVersionCode = currentVersionCode,
                expectedSha256 = release.apkSha256
            )

            if (!verification.isValid) {
                targetFile.delete()
                return@withContext Result.failure(
                    SecurityException("APK Verification Failed: ${verification.errorReason ?: "Unknown validation error"}")
                )
            }

            Result.success(targetFile)

        } catch (e: Exception) {
            targetFile.delete()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
