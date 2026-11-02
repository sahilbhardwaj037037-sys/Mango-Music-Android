package com.mangomusic.feedback

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FeedbackClient(private val context: Context) {

    fun collectSubmission(
        category: FeedbackCategory,
        message: String,
        email: String?
    ): FeedbackSubmission {
        val packageManager = context.packageManager
        val pInfo = try {
            packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val versionName = pInfo?.versionName ?: "1.0"
        val versionCode = pInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 1L

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = isoFormat.format(Date())

        return FeedbackSubmission(
            category = category,
            message = message.trim(),
            email = email?.trim()?.takeIf { it.isNotBlank() },
            appName = "Mango Music",
            versionName = versionName,
            versionCode = versionCode,
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER ?: "Unknown",
            deviceModel = Build.MODEL ?: "Unknown",
            timestamp = timestamp
        )
    }

    suspend fun submitFeedback(
        submission: FeedbackSubmission,
        overrideUrl: String? = null
    ): FeedbackSubmissionResult = withContext(Dispatchers.IO) {
        val endpointUrl = overrideUrl?.takeIf { it.isNotBlank() } ?: FeedbackConfig.getEndpointUrl(context)

        if (endpointUrl.isBlank()) {
            return@withContext FeedbackSubmissionResult.Error(
                "Feedback service endpoint is pending Google Apps Script deployment. Please deploy the script and configure the Web App URL."
            )
        }

        if (!endpointUrl.startsWith("https://")) {
            return@withContext FeedbackSubmissionResult.Error(
                "Secure HTTPS connection is required for feedback submission."
            )
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("category", submission.category.label)
                put("message", submission.message)
                put("email", submission.email ?: "")
                put("appName", submission.appName)
                put("versionName", submission.versionName)
                put("versionCode", submission.versionCode)
                put("androidRelease", submission.androidRelease)
                put("sdkInt", submission.sdkInt)
                put("deviceManufacturer", submission.deviceManufacturer)
                put("deviceModel", submission.deviceModel)
                put("timestamp", submission.timestamp)
            }.toString()

            var currentUrl = endpointUrl
            var connection: HttpURLConnection? = null
            var responseCode = -1
            var responseBody = ""
            var redirectCount = 0
            val maxRedirects = 5

            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = false
                    if (redirectCount == 0) {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "application/json")
                    } else {
                        // Following HTTP 302/307 redirect typically returned by Apps Script Web App
                        requestMethod = "GET"
                        doOutput = false
                        setRequestProperty("Accept", "application/json")
                    }
                }

                if (redirectCount == 0) {
                    connection.outputStream.use { os ->
                        os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    }
                }

                responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location != null) {
                        currentUrl = location
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val inputStream = if (responseCode in 200..299) {
                connection?.inputStream
            } else {
                connection?.errorStream
            }

            responseBody = inputStream?.let { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            connection?.disconnect()

            if (responseCode in 200..299) {
                val status = try {
                    val resJson = JSONObject(responseBody)
                    resJson.optString("status", "success")
                } catch (_: Exception) {
                    "success"
                }

                if (status.equals("error", ignoreCase = true)) {
                    val errMsg = try {
                        JSONObject(responseBody).optString("message", "Submission error from server")
                    } catch (_: Exception) {
                        "Submission error from server"
                    }
                    FeedbackSubmissionResult.Error("Submission failed: $errMsg")
                } else {
                    FeedbackSubmissionResult.Success("Feedback sent successfully. Thank you!")
                }
            } else {
                FeedbackSubmissionResult.Error("Server returned response code $responseCode. Please try again later.")
            }
        } catch (e: java.net.UnknownHostException) {
            FeedbackSubmissionResult.Error("No internet connection. Please check your network and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            FeedbackSubmissionResult.Error("Connection timed out. Please try again.")
        } catch (e: Exception) {
            FeedbackSubmissionResult.Error("Failed to send feedback: ${e.localizedMessage ?: "Network error"}")
        }
    }
}
