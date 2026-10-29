package com.mangomusic.runtime

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** All calls must run on worker threads. Native architecture dependencies stay in this module. */
class YtDlpRuntime(private val context: Context) {
    fun execute(operation: String, value: String, cancelled: AtomicBoolean): String {
        synchronized(startLock) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        }
        val qjs = File(context.applicationInfo.nativeLibraryDir, "libmangoqjs.so")
        check(qjs.canExecute()) { "JavaScript runtime is not installed for this device architecture." }
        return Python.getInstance().getModule("mango_online")
            .callAttr("execute", operation, value, qjs.absolutePath, cancelled, context.cacheDir.absolutePath).toString()
    }
    private companion object { val startLock = Any() }
}
