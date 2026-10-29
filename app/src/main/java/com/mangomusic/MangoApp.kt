package com.mangomusic

import android.app.Application
import com.mangomusic.data.LibraryRepository

class MangoApp: Application() {
    val repository by lazy { LibraryRepository(this) }
    val audioDiagnostics by lazy { com.mangomusic.playback.AudioPlaybackDiagnostics(this) }
    val mix by lazy { com.mangomusic.playback.MixSettings(this) }
    val online by lazy { com.mangomusic.online.OnlineRepository(this) }
}
