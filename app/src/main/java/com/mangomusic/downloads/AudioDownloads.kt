package com.mangomusic.downloads

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.mangomusic.MangoApp
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.Track
import com.mangomusic.runtime.YtDlpRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Durable downloads have their own executor, files and work leases; no playback resolver changes. */
class AudioDownloads(private val app:MangoApp) {
    private val dao get()=app.repository.dao
    private val work by lazy { WorkManager.getInstance(app) }
    private val mutex=Mutex()
    val root=File(app.filesDir,"downloads")
    suspend fun enqueue(track:Track,permission:Boolean) = mutex.withLock {
        require(permission) { DownloadFailure("permission").message!! }
        require(DownloadPolicy.eligible(track)) { DownloadFailure("unsupported").message!! }
        require(!app.online.offline.value) { DownloadFailure("offline").message!! }
        app.repository.add(track)
        val old=dao.get(track.id)
        if(local(old)!=null || old?.downloadId?.let { it>0 }==true) return@withLock
        val running=withContext(Dispatchers.IO) { work.getWorkInfosForUniqueWork(name(track.id)).get() }.any { !it.state.isFinished }
        if(running)return@withLock
        val request=OneTimeWorkRequestBuilder<AudioDownloadWorker>().setInputData(workDataOf("track" to track.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        dao.queueDownload(track.id,request.id.toString())
        withContext(Dispatchers.IO) { work.enqueueUniqueWork(name(track.id),ExistingWorkPolicy.KEEP,request).result.get() }
    }
    suspend fun cancel(id:String) = mutex.withLock {
        // Invalidate the lease before cancellation so late callbacks cannot publish a file.
        dao.stopDownload(id,"CANCELLED",null)
        withContext(Dispatchers.IO) { work.cancelUniqueWork(name(id)).result.get() }
    }
    suspend fun pauseAll() {
        val rows=withContext(Dispatchers.IO) { app.repository.db.openHelper.readableDatabase.query("SELECT id FROM tracks WHERE downloadState IN ('QUEUED','DOWNLOADING','VERIFYING')").use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } } }
        rows.forEach { cancel(it);dao.stopDownload(it,"PAUSED","Paused for Offline Mode. Retry when online.") }
    }
    fun local(row:SavedTrack?):String? = row?.localUri?.takeIf { uri ->
        runCatching { val p=Uri.parse(uri);p.scheme=="file" && File(requireNotNull(p.path)).let { it.isFile && it.length()>0 } }.getOrDefault(false)
    }
    suspend fun remove(id:String) = mutex.withLock {
        val row=dao.get(id) ?: return@withLock
        dao.stopDownload(id,"CANCELLED",null)
        withContext(Dispatchers.IO) { work.cancelUniqueWork(name(id)).result.get()
            listOfNotNull(row.localUri,row.localArtworkUri).forEach { uri ->
                val file=runCatching { File(requireNotNull(Uri.parse(uri).path)).canonicalFile }.getOrNull() ?: return@forEach
                val owned=file.parentFile==File(root,"media").canonicalFile && file.name.startsWith("${DownloadPolicy.key(id)}-")
                val legacy=file.parentFile==app.getExternalFilesDir("audio")?.canonicalFile && file.name=="$id.audio"
                if(owned || legacy) check(!file.exists() || file.delete()) { "Could not remove saved audio. Retry." }
            }
        }
        dao.clearDownload(id)
    }
    suspend fun reconcile(rows:List<SavedTrack>) = mutex.withLock {
        for(snapshot in rows) {
            val row=dao.get(snapshot.id) ?: continue
            if(row.localUri!=null && local(row)==null) {
                dao.clearDownload(row.id);dao.stopDownload(row.id,"MISSING",DownloadFailure("missing").message)
            } else if(row.downloadState in DownloadPolicy.active && row.downloadWork!=null) {
                val info=withContext(Dispatchers.IO) { work.getWorkInfoById(UUID.fromString(row.downloadWork)).get() }
                if(info==null || info.state.isFinished) dao.stopDownload(row.id,if(info?.state==WorkInfo.State.CANCELLED) "CANCELLED" else "FAILED","Download interrupted. Retry to resume.")
            }
        }
    }
    private fun name(id:String)="audio-${DownloadPolicy.key(id)}"
}

class AudioDownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    private val app=context.applicationContext as MangoApp
    private val dao get()=app.repository.dao
    override suspend fun doWork():Result {
        val trackId=inputData.getString("track") ?: return Result.failure()
        val work=id.toString(); val attempt=UUID.randomUUID().toString()
        val row=dao.get(trackId) ?: return Result.failure()
        if(!row.downloadPermission || app.online.offline.value) {
            if(row.downloadWork==work)dao.stopDownload(trackId,"PAUSED",DownloadFailure("offline").message)
            return Result.failure()
        }
        if(dao.claimDownload(trackId,work,attempt)==0)return Result.success()
        val cancelled=AtomicBoolean(false)
        val staging=File(app.downloads.root,"staging/$attempt")
        var published:File?=null;var art:File?=null
        try {
            setForeground(foreground(row.title,0,0))
            val result=background(cancelled,staging) {
                staging.mkdirs()
                if(!DownloadPolicy.hasSpace(staging.usableSpace))throw DownloadFailure("storage")
                val prefix="${DownloadPolicy.key(trackId)}-$work"
                val media=File(app.downloads.root,"media").apply { mkdirs() }
                // A process may have died between atomic publication and the Room transaction.
                val recovered=media.listFiles()?.firstOrNull { it.name.startsWith("$prefix.") && !it.name.endsWith(".art") }
                val raw=recovered ?: if(DownloadPolicy.youtube(row.track())) {
                    val progress=Progress { bytes,total ->
                        if(cancelled.get())throw java.io.InterruptedIOException()
                        runBlocking { dao.downloadProgress(trackId,work,attempt,"DOWNLOADING",bytes,total);setForeground(foreground(row.title,bytes,total)) }
                    }
                    val json=JSONObject(YtDlpRuntime(app).download(trackId.removePrefix("youtube:"),staging.absolutePath,cancelled,progress,DownloadPolicy.MAX_BYTES,DownloadPolicy.RESERVE_BYTES))
                    if(json.has("error"))throw DownloadFailure(json.getString("error"))
                    File(json.getString("path")).also { if(it.canonicalFile.parentFile!=staging.canonicalFile)throw DownloadFailure("invalid") }
                } else {
                    val file=File(staging,"audio")
                    transfer(row.uri,file,cancelled) { bytes,total -> runBlocking { dao.downloadProgress(trackId,work,attempt,"DOWNLOADING",bytes,total);setForeground(foreground(row.title,bytes,total)) } };file
                }
                if(cancelled.get())throw java.io.InterruptedIOException()
                runBlocking { dao.downloadProgress(trackId,work,attempt,"VERIFYING",raw.length(),raw.length()) }
                val verified=verify(raw,staging,cancelled)
                val final=if(raw==recovered)raw else File(media,"$prefix.${verified.first.extension.ifBlank { "audio" }}").also {
                    Files.move(verified.first.toPath(),it.toPath(),StandardCopyOption.ATOMIC_MOVE)
                }
                published=final
                val sha=final.inputStream().use { input -> val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) };digest.digest().joinToString("") { "%02x".format(it) } }
                val thumbnail=row.thumbnailUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                    runCatching { val image=File(staging,"art"); transfer(url,image,cancelled,max=5L*1024*1024) { _,_-> };File(media,"$prefix.art").also { Files.move(image.toPath(),it.toPath(),StandardCopyOption.ATOMIC_MOVE) } }.getOrNull()
                }
                art=thumbnail
                if(cancelled.get())throw java.io.InterruptedIOException()
                Triple(final,verified.second,sha)
            }
            currentCoroutineContext().ensureActive()
            if(app.online.offline.value)throw DownloadFailure("offline")
            val updated=dao.completeDownload(trackId,work,attempt,Uri.fromFile(result.first).toString(),art?.let { Uri.fromFile(it).toString() },result.second,result.first.length(),result.third)
            if(updated==0) { published?.delete();art?.delete() }
            return Result.success()
        } catch(e:CancellationException) { cancelled.set(true);throw e }
        catch(e:Exception) {
            dao.failDownload(trackId,work,attempt,(e as? DownloadFailure)?.message ?: DownloadFailure("network").message!!)
            return Result.failure()
        } finally {
            // Invalidate callbacks immediately; executor cleanup runs after blocking I/O unwinds.
            cancelled.set(true)
            withContext(NonCancellable+Dispatchers.IO) { if(dao.get(trackId)?.downloadState!="COMPLETED") { published?.delete();art?.delete() } }
        }
    }
    private fun foreground(title:String,bytes:Long,total:Long):ForegroundInfo {
        val manager=app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads","Audio downloads",NotificationManager.IMPORTANCE_LOW))
        val notification=NotificationCompat.Builder(app,"downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(if(total>0) "Saving audio · ${DownloadPolicy.percent(bytes,total)}%" else "Saving audio…")
            .setProgress(100,DownloadPolicy.percent(bytes,total) ?: 0,total<=0).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0,"Cancel",WorkManager.getInstance(app).createCancelPendingIntent(id)).build()
        return ForegroundInfo(id.hashCode(),notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    /** Public callback called by Chaquopy; throttle Room/notification work to four times per second. */
    class Progress(private val action:(Long,Long)->Unit) {
        private var last=0L
        fun update(bytes:Long,total:Long) { val now=android.os.SystemClock.elapsedRealtime();if(now-last>=250 || bytes==total) { last=now;action(bytes,total) } }
    }
    private suspend fun <T> background(cancelled:AtomicBoolean,staging:File,action:()->T):T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancelled.set(true) }
        executor.execute {
            try { val value=action(); if(continuation.isActive)continuation.resume(value) }
            catch(e:Exception) { if(continuation.isActive)continuation.resumeWithException(e) }
            finally { // Only private UUID staging directories are cleaned by the task that owns them.
                if(continuation.isCancelled) {
                    val trackId=inputData.getString("track")!!
                    val prefix="${DownloadPolicy.key(trackId)}-$id."
                    val saved=runBlocking { dao.get(trackId) };val kept=listOfNotNull(saved?.localUri,saved?.localArtworkUri)
                    File(app.downloads.root,"media").listFiles()?.filter { it.name.startsWith(prefix) && Uri.fromFile(it).toString() !in kept }?.forEach { it.delete() }
                }
                staging.deleteRecursively()
                File(app.downloads.root,"staging").listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}")) && System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }?.forEach { it.deleteRecursively() }
            }
        }
    }
    companion object {
        private val executor=Executors.newSingleThreadExecutor { task -> Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run() },"mango-download") }
        fun transfer(url:String,file:File,cancelled:AtomicBoolean,max:Long=DownloadPolicy.MAX_BYTES,progress:(Long,Long)->Unit) {
            var address=url
            repeat(6) {
                if(cancelled.get())throw java.io.InterruptedIOException()
                require(Uri.parse(address).scheme=="https") { "Only HTTPS downloads are supported." }
                val connection=URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout=12000;connection.readTimeout=12000;connection.instanceFollowRedirects=false
                try {
                    val code=connection.responseCode
                    if(code in 300..399) { address=URL(URL(address),connection.getHeaderField("Location") ?: throw DownloadFailure("network")).toString();return@repeat }
                    if(code in listOf(401,403,451))throw DownloadFailure("restricted")
                    if(code!=200)throw DownloadFailure("network")
                    val total=connection.contentLengthLong.coerceAtLeast(0)
                    if(total>max)throw DownloadFailure("size")
                    if(!DownloadPolicy.hasSpace(file.parentFile!!.usableSpace,total))throw DownloadFailure("storage")
                    connection.inputStream.use { input -> file.outputStream().use { output ->
                        val buffer=ByteArray(65536);var bytes=0L;var last=0L
                        while(true) {
                            if(cancelled.get())throw java.io.InterruptedIOException()
                            val n=input.read(buffer);if(n<0)break
                            bytes+=n;if(bytes>max)throw DownloadFailure("size")
                            if(file.parentFile!!.usableSpace<DownloadPolicy.RESERVE_BYTES)throw DownloadFailure("storage")
                            output.write(buffer,0,n)
                            val now=android.os.SystemClock.elapsedRealtime();if(now-last>=250) { progress(bytes,total);last=now }
                        }
                        if(total>0 && bytes!=total)throw DownloadFailure("network")
                        progress(bytes,total)
                    } }
                    return
                } finally { connection.disconnect() }
            }
            throw DownloadFailure("network")
        }
        /** Remux AAC transport streams without decoding or changing any samples. */
        fun verify(file:File,staging:File,cancelled:AtomicBoolean):Pair<File,Long> {
            if(!file.isFile || file.length()==0L)throw DownloadFailure("invalid")
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val audio=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: throw DownloadFailure("invalid")
                val format=extractor.getTrackFormat(audio);extractor.selectTrack(audio)
                var duration=if(format.containsKey(MediaFormat.KEY_DURATION))format.getLong(MediaFormat.KEY_DURATION)/1000 else 0L
                var result=file
                val header=file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
                val mp4=String(header,4,4,Charsets.US_ASCII)=="ftyp"
                if(format.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm" && !mp4) {
                    result=File(staging,"verified.m4a")
                    val muxer=MediaMuxer(result.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    try {
                        val target=muxer.addTrack(format);muxer.start()
                        val buffer=ByteBuffer.allocate(2*1024*1024);val info=MediaCodec.BufferInfo();var count=0;var end=0L
                        while(true) {
                            if(cancelled.get())throw java.io.InterruptedIOException()
                            buffer.clear();val n=extractor.readSampleData(buffer,0);if(n<0)break
                            if(n>buffer.capacity())throw DownloadFailure("invalid")
                            info.set(0,n,extractor.sampleTime,if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            muxer.writeSampleData(target,buffer,info);end=extractor.sampleTime;count++;extractor.advance()
                        }
                        if(count==0)throw DownloadFailure("invalid")
                        duration=maxOf(duration,end/1000);muxer.stop()
                    } finally { muxer.release() }
                } else {
                    val meta=MediaMetadataRetriever()
                    try { meta.setDataSource(file.absolutePath);duration=maxOf(duration,meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) }
                    finally { meta.release() }
                }
                if(duration<=0)throw DownloadFailure("invalid")
                return result to duration
            } finally { extractor.release() }
        }
    }
}
