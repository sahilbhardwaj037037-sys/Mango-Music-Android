package com.mangomusic.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object UpdateInstaller {

    fun launchPackageInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(java.io.FileNotFoundException("Update APK file does not exist."))
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
