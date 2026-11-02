package com.mangomusic.updater

import java.io.File

data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val publishedAt: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String?
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val currentVersionCode: Long) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progressFraction: Float
) {
    val progressPercent: Int get() = (progressFraction * 100).toInt().coerceIn(0, 100)
    val downloadedMb: String get() = "%.1f".format(downloadedBytes / (1024.0 * 1024.0))
    val totalMb: String get() = "%.1f".format(totalBytes / (1024.0 * 1024.0))
}

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Available(val release: ReleaseInfo) : UpdateUiState()
    data class Downloading(val release: ReleaseInfo, val progress: DownloadProgress) : UpdateUiState()
    data class Verifying(val release: ReleaseInfo) : UpdateUiState()
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateUiState()
}

data class ApkVerificationResult(
    val isValid: Boolean,
    val errorReason: String? = null,
    val packageName: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val certificateSha256: String? = null
)
