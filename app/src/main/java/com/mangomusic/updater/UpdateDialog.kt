package com.mangomusic.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File

private val GoldAccent = Color(0xFFE5B842)
private val DialogBg = Color(0xFF18181B)
private val BorderColor = Color(0xFF2C2C30)

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersionName: String,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: (ReleaseInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DialogBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("app-update-dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    is UpdateUiState.Available -> {
                        AvailableContent(
                            release = state.release,
                            currentVersionName = currentVersionName,
                            onStartDownload = { onStartDownload(state.release) },
                            onDismiss = onDismiss
                        )
                    }
                    is UpdateUiState.Downloading -> {
                        DownloadingContent(
                            release = state.release,
                            progress = state.progress,
                            onCancel = { onCancelDownload(state.release) }
                        )
                    }
                    is UpdateUiState.Verifying -> {
                        VerifyingContent(release = state.release)
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        ReadyToInstallContent(
                            release = state.release,
                            onInstall = { onInstall(state.apkFile) },
                            onDismiss = onDismiss
                        )
                    }
                    is UpdateUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            release = state.release,
                            onRetry = { state.release?.let { onStartDownload(it) } },
                            onDismiss = onDismiss
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun AvailableContent(
    release: ReleaseInfo,
    currentVersionName: String,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldAccent.copy(alpha = 0.14f))
                .border(0.5.dp, GoldAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Mango Music v${release.versionName} (current: v$currentVersionName)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (release.releaseNotes.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF121214),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = "What's New:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldAccent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = release.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (release.apkSize > 0L) {
        val mbSize = "%.1f MB".format(release.apkSize / (1024.0 * 1024.0))
        Text(
            text = "Download size: $mbSize",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onStartDownload,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldAccent,
                contentColor = Color(0xFF121214)
            )
        ) {
            Text("Update Now", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadingContent(
    release: ReleaseInfo,
    progress: DownloadProgress,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Downloading Update...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Mango Music v${release.versionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinearProgressIndicator(
            progress = { progress.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = GoldAccent,
            trackColor = Color(0xFF28282C)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${progress.downloadedMb} MB / ${progress.totalMb} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${progress.progressPercent}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = GoldAccent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VerifyingContent(release: ReleaseInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = GoldAccent
        )
        Column {
            Text(
                text = "Verifying Package...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Checking certificate and security integrity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    release: ReleaseInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(GoldAccent.copy(alpha = 0.14f))
                .border(1.dp, GoldAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "Update Ready to Install",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Mango Music v${release.versionName} has been downloaded and verified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = Color(0xFF121214)
                )
            ) {
                Text("Install Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    release: ReleaseInfo?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Update Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (release != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = Color(0xFF121214)
                    )
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
