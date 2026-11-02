package com.mangomusic.feedback

import android.content.Context

object FeedbackConfig {
    /**
     * Target Google Apps Script Web App HTTPS URL.
     * When deployed, set your Apps Script Web App URL here or configure via SharedPreferences.
     * Example: "https://script.google.com/macros/s/AKfycbx.../exec"
     */
    const val DEFAULT_WEB_APP_URL: String =
        "https://script.google.com/macros/s/AKfycby7_nqHQLP9yWPQcOS8shF08RXNlEgjR5KVY4DfpSVIn7hmUPQ_xn1DXIEvZAPRG2jiSA/exec"
    const val NOTIFICATION_EMAIL: String = "bysahilworks@gmail.com"
    private const val PREFS_NAME = "mango_feedback_config"
    private const val KEY_ENDPOINT_URL = "custom_endpoint_url"

    fun getEndpointUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_ENDPOINT_URL, null)?.trim()
        return if (!custom.isNullOrBlank()) custom else DEFAULT_WEB_APP_URL
    }

    fun setCustomEndpointUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (url.isNullOrBlank()) {
                remove(KEY_ENDPOINT_URL)
            } else {
                putString(KEY_ENDPOINT_URL, url.trim())
            }
            apply()
        }
    }
}
