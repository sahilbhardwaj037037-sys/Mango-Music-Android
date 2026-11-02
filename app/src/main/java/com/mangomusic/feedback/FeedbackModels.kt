package com.mangomusic.feedback

enum class FeedbackCategory(val label: String) {
    BUG_REPORT("Bug Report"),
    FEATURE_REQUEST("Feature Request"),
    MUSIC_EXPERIENCE("Music Experience"),
    OTHER("Other")
}

data class FeedbackSubmission(
    val category: FeedbackCategory,
    val message: String,
    val email: String? = null,
    val appName: String = "Mango Music",
    val versionName: String,
    val versionCode: Long,
    val androidRelease: String,
    val sdkInt: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val timestamp: String
)

sealed class FeedbackSubmissionResult {
    data class Success(val message: String) : FeedbackSubmissionResult()
    data class Error(val message: String) : FeedbackSubmissionResult()
}
