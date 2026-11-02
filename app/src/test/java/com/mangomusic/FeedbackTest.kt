package com.mangomusic

import com.mangomusic.feedback.*
import org.junit.Assert.*
import org.junit.Test

class FeedbackTest {

    @Test
    fun feedbackCategoriesContainExpectedLabels() {
        assertEquals("Bug Report", FeedbackCategory.BUG_REPORT.label)
        assertEquals("Feature Request", FeedbackCategory.FEATURE_REQUEST.label)
        assertEquals("Music Experience", FeedbackCategory.MUSIC_EXPERIENCE.label)
        assertEquals("Other", FeedbackCategory.OTHER.label)
    }

    @Test
    fun feedbackSubmissionConstructsCorrectPayload() {
        val submission = FeedbackSubmission(
            category = FeedbackCategory.BUG_REPORT,
            message = "Playback stops on track change",
            email = "tester@example.com",
            appName = "Mango Music",
            versionName = "1.0",
            versionCode = 1L,
            androidRelease = "15",
            sdkInt = 35,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9 Pro",
            timestamp = "2026-11-02T14:45:00Z"
        )

        assertEquals(FeedbackCategory.BUG_REPORT, submission.category)
        assertEquals("Playback stops on track change", submission.message)
        assertEquals("tester@example.com", submission.email)
        assertEquals("Mango Music", submission.appName)
        assertEquals("1.0", submission.versionName)
        assertEquals(1L, submission.versionCode)
        assertEquals("Google", submission.deviceManufacturer)
        assertEquals("Pixel 9 Pro", submission.deviceModel)
        assertEquals("15", submission.androidRelease)
        assertEquals(35, submission.sdkInt)
        assertEquals("2026-11-02T14:45:00Z", submission.timestamp)
    }

    @Test
    fun feedbackSubmissionWithoutOptionalEmail() {
        val submission = FeedbackSubmission(
            category = FeedbackCategory.FEATURE_REQUEST,
            message = "Please add equalizer presets",
            email = null,
            appName = "Mango Music",
            versionName = "1.0",
            versionCode = 1L,
            androidRelease = "14",
            sdkInt = 34,
            deviceManufacturer = "Samsung",
            deviceModel = "Galaxy S24",
            timestamp = "2026-11-02T15:00:00Z"
        )

        assertNull(submission.email)
        assertEquals("Please add equalizer presets", submission.message)
        assertEquals(FeedbackCategory.FEATURE_REQUEST, submission.category)
    }

    @Test
    fun feedbackSubmissionResultTypes() {
        val success: FeedbackSubmissionResult = FeedbackSubmissionResult.Success("Feedback sent")
        assertTrue(success is FeedbackSubmissionResult.Success)
        assertEquals("Feedback sent", (success as FeedbackSubmissionResult.Success).message)

        val error: FeedbackSubmissionResult = FeedbackSubmissionResult.Error("Network failure")
        assertTrue(error is FeedbackSubmissionResult.Error)
        assertEquals("Network failure", (error as FeedbackSubmissionResult.Error).message)
    }
}
