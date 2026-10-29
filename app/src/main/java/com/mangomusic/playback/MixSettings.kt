package com.mangomusic.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class MixStatus(val loading: Boolean=false,val message: String?=null,val source: String?=null,val retryable: Boolean=false)
class MixSettings(context: Context) {
    private val prefs=context.getSharedPreferences("appearance",0)
    val enabled=MutableStateFlow(prefs.getBoolean("mix",false))
    val status=MutableStateFlow(MixStatus())
    val retry=MutableStateFlow(0)
    fun enable(value: Boolean) { prefs.edit().putBoolean("mix",value).apply();enabled.value=value }
    fun retry() { retry.value++ }
}
