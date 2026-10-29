package com.mangomusic

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SignedReleaseTest {
    @Test fun releaseIsNonDebuggableAndIncludesExecutableNativeRuntime() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Release-specific check",context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE==0)
        val app=context.applicationContext as MangoApp
        val info=context.packageManager.getPackageInfo(context.packageName,0)
        assertEquals("com.mangomusic",context.packageName)
        assertTrue(info.versionName!!.matches(Regex("[0-9]+(\\.[0-9]+){1,2}")))
        assertTrue(androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)>0L)
        assertFalse(app.audioDiagnostics.enabled)
        assertFalse(app.audioDiagnostics.fingerprintPcm)
        assertTrue("QuickJS must remain an extracted executable in release",File(context.applicationInfo.nativeLibraryDir,"libmangoqjs.so").canExecute())
        assertTrue(File(context.applicationInfo.nativeLibraryDir,"libpython3.14.so").isFile)
    }
}
