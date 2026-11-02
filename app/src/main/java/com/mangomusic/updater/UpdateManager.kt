package com.mangomusic.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.mangomusic.MainActivity
import com.mangomusic.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class UpdateManager(
    private val context: Context,
    private val checker: UpdateChecker = UpdateChecker(),
    private val downloader: UpdateDownloader = UpdateDownloader(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val prefs = context.getSharedPreferences("mango_updater_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private var downloadJob: Job? = null
    private var lastDismissedVersionCode: Long = 0L

    val currentVersionName: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    val currentVersionCode: Long by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            1L
        }
    }

    init {
        createNotificationChannel()
    }

    fun checkForUpdates(isManual: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_check_timestamp", 0L)

        if (!isManual && (now - lastCheck < UpdateConfig.AUTO_CHECK_INTERVAL_MS)) {
            // Checked recently; skip to avoid rate limits and unnecessary network use
            return
        }

        _uiState.value = UpdateUiState.Checking

        scope.launch {
            val result = checker.checkLatestRelease(currentVersionCode)
            prefs.edit().putLong("last_check_timestamp", System.currentTimeMillis()).apply()

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.value = UpdateUiState.Available(result.release)
                    val wasDismissed = lastDismissedVersionCode == result.release.versionCode
                    if (isManual || !wasDismissed) {
                        _dialogVisible.value = true
                    }
                    if (!isManual) {
                        postUpdateNotification(result.release)
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _uiState.value = UpdateUiState.UpToDate(result.currentVersion)
                }
                is UpdateCheckResult.Error -> {
                    _uiState.value = UpdateUiState.Error(result.message)
                }
            }
        }
    }

    fun startDownload(release: ReleaseInfo) {
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Downloading(
            release,
            DownloadProgress(0L, release.apkSize, 0f)
        )

        downloadJob = scope.launch {
            val result = downloader.downloadAndVerify(
                release = release,
                currentVersionCode = currentVersionCode,
                onProgress = { progress ->
                    _uiState.value = UpdateUiState.Downloading(release, progress)
                }
            )

            result.fold(
                onSuccess = { apkFile ->
                    _uiState.value = UpdateUiState.ReadyToInstall(release, apkFile)
                },
                onFailure = { error ->
                    _uiState.value = UpdateUiState.Error(
                        error.localizedMessage ?: "Failed to download update.",
                        release
                    )
                }
            )
        }
    }

    fun cancelDownload(release: ReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = UpdateUiState.Available(release)
    }

    fun installApk(apkFile: File) {
        UpdateInstaller.launchPackageInstaller(context, apkFile).onFailure { error ->
            _uiState.value = UpdateUiState.Error("Installation launch failed: ${error.localizedMessage}")
        }
    }

    fun showDialog() {
        _dialogVisible.value = true
    }

    fun dismissDialog() {
        val currentState = _uiState.value
        if (currentState is UpdateUiState.Available) {
            lastDismissedVersionCode = currentState.release.versionCode
        }
        _dialogVisible.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateConfig.NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when a new Mango Music release is available."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun postUpdateNotification(release: ReleaseInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action_show_update", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            UpdateConfig.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.mango_logo)
            .setContentTitle("Mango Music v${release.versionName} Available")
            .setContentText("A new version is ready. Tap to view release notes and update.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(UpdateConfig.NOTIFICATION_ID, notification)
    }
}
