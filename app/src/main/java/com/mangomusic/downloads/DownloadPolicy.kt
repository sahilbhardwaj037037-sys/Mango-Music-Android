package com.mangomusic.downloads

import com.mangomusic.domain.Track
import java.security.MessageDigest

object DownloadPolicy {
    const val MAX_BYTES=500L*1024*1024
    const val RESERVE_BYTES=32L*1024*1024
    val active=setOf("QUEUED","DOWNLOADING","VERIFYING")
    fun youtube(track:Track)=track.id.matches(Regex("youtube:[A-Za-z0-9_-]{11}"))
    fun eligible(track:Track)=youtube(track) || track.downloadable && track.uri.startsWith("https://")
    fun key(id:String)=MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    fun hasSpace(available:Long,expected:Long=0)=available>=RESERVE_BYTES+expected.coerceAtLeast(0) && expected<=MAX_BYTES
    fun percent(bytes:Long,total:Long)=if(total<=0) null else ((bytes.coerceAtLeast(0).toDouble()/total)*100).toInt().coerceIn(0,100)
}
class DownloadFailure(val reason:String):java.io.IOException(when(reason) {
    "permission" -> "Confirm permission from the source and content owner before downloading."
    "restricted" -> "The source restricts this download. Sign-in, DRM and access-control bypass are not supported."
    "storage" -> "Not enough storage. Free space and retry."
    "size" -> "This audio exceeds the 500 MB download limit."
    "offline" -> "Turn off Offline Mode to download."
    "unsupported" -> "The source did not provide a supported downloadable audio format."
    "invalid" -> "The downloaded file is not valid audio. Retry or choose another source."
    "missing" -> "The saved audio is missing. Download it again."
    else -> "Download failed. Check your connection and source permission, then retry."
})
