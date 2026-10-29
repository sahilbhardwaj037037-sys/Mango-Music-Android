package com.mangomusic.online

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mangomusic.domain.Track
import com.mangomusic.runtime.YtDlpRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OnlineFailure(val kind: String): IOException(when (kind) {
    "offline" -> "Online audio is unavailable in Offline Mode."
    "restricted" -> "This content requires access that Mango Music does not provide. Choose another track."
    "network" -> "Could not reach the music provider. Check your connection and retry."
    "timeout" -> "The music provider took too long to respond. Please retry."
    "runtime" -> "The on-device music runtime could not start. Check the app installation."
    else -> "This track is unavailable or its audio could not be resolved. Try another result."
})

data class ResolvedAudio(val uri: String, val mimeType: String, val headers: Map<String,String>, val validUntil: Long)
data class OnlineSearchState(val query: String = "", val loading: Boolean = false, val tracks: List<Track> = emptyList(), val error: String? = null)

/** Catalog metadata and transient audio resolution are separate; no resolved URL is persisted. */
class OnlineRepository(context: Context) {
    private val runtime = YtDlpRuntime(context.applicationContext)
    private val executor = Executors.newFixedThreadPool(3)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String,ResolvedAudio>()
    private val activeCalls = java.util.concurrent.ConcurrentHashMap.newKeySet<AtomicBoolean>()
    private val inFlight = mutableMapOf<String,Deferred<ResolvedAudio>>()
    val offline = MutableStateFlow(context.getSharedPreferences("appearance",0).getBoolean("offline",false))
    val requestsStarted = java.util.concurrent.atomic.AtomicInteger(0)

    fun setOffline(value: Boolean) {
        offline.value = value
        if (value) activeCalls.forEach { it.set(true) }
        if (value) scope.launch { mutex.withLock { inFlight.values.forEach { it.cancel() }; inFlight.clear(); cache.clear() } }
    }
    private suspend fun call(operation: String, value: String): JSONObject = withTimeout(45000) {
        if (operation != "diagnostics" && offline.value) throw OnlineFailure("offline")
        val raw = suspendCancellableCoroutine<String> { continuation ->
            val cancelled = AtomicBoolean(false)
            activeCalls.add(cancelled)
            val future = executor.submit {
                val start = System.currentTimeMillis()
                try {
                    if (cancelled.get()) return@submit
                    if (operation != "diagnostics") {
                        if (offline.value) throw OnlineFailure("offline")
                        requestsStarted.incrementAndGet()
                    }
                    val result = runtime.execute(operation,value,cancelled)
                    if (continuation.isActive) continuation.resume(result)
                    Log.d("MangoOnline", "$operation completed in ${System.currentTimeMillis()-start}ms")
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(if (failure is OnlineFailure) failure else OnlineFailure("runtime"))
                    Log.w("MangoOnline", "$operation failed (${failure.javaClass.simpleName})")
                } finally { activeCalls.remove(cancelled) }
            }
            continuation.invokeOnCancellation { cancelled.set(true); future.cancel(true); activeCalls.remove(cancelled) }
        }
        if (offline.value && operation != "diagnostics") throw OnlineFailure("offline")
        val json = JSONObject(raw)
        if (json.has("error")) {
            if (json.optString("error") == "cancelled") throw CancellationException()
            throw OnlineFailure(json.optString("error"))
        }
        json
    }
    suspend fun diagnostics(): JSONObject = call("diagnostics", "")
    suspend fun search(query: String): List<Track> {
        val term = query.trim()
        require(term.isNotBlank() && term.length <= 200 && term.none { it.isISOControl() })
        val array = call("search",term).getJSONArray("tracks")
        return (0 until array.length()).map { index ->
            val item=array.getJSONObject(index)
            Track(item.getString("id"),item.getString("title"),item.getString("artist"),item.getString("uri"),
                item.optLong("durationMs"),false,item.optString("thumbnailUrl").takeIf { it.startsWith("https://") })
        }.distinctBy { it.id }
    }
    suspend fun resolve(id: String): ResolvedAudio {
        if (offline.value) throw OnlineFailure("offline")
        require(id.matches(Regex("youtube:[A-Za-z0-9_-]{11}")))
        val job = mutex.withLock {
            cache[id]?.takeIf { it.validUntil > System.currentTimeMillis() }?.let { return it }
            inFlight[id] ?: scope.async(start=CoroutineStart.LAZY) {
                val json=call("resolve",id.removePrefix("youtube:"))
                val url=json.getString("url")
                require(Uri.parse(url).scheme=="https")
                val rawHeaders=json.optJSONObject("headers") ?: JSONObject()
                val headers=rawHeaders.keys().asSequence().associateWith { rawHeaders.getString(it) }
                    .filter { (key,value) -> '\n' !in key && '\r' !in key && '\n' !in value && '\r' !in value }
                val expiration=Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)?.minus(60000)
                val audio=ResolvedAudio(url,json.getString("mimeType"),headers,minOf(System.currentTimeMillis()+180000,expiration ?: Long.MAX_VALUE))
                mutex.withLock {
                    if (!offline.value) {
                        cache.entries.removeAll { it.value.validUntil <= System.currentTimeMillis() }
                        if (cache.size >= 24 && id !in cache) cache.remove(cache.keys.first())
                        cache[id]=audio
                    }
                }
                audio
            }.also { created ->
                inFlight[id]=created
                created.invokeOnCompletion {
                    scope.launch { mutex.withLock { if (inFlight[id]===created) inFlight.remove(id) } }
                }
                created.start()
            }
        }
        val audio=job.await()
        if (offline.value) throw OnlineFailure("offline")
        return audio
    }

    fun prefetch(id: String) { if (id.startsWith("youtube:") && !offline.value) scope.launch { try { resolve(id) } catch (_: Exception) { /* Playback surfaces a useful error if selected. */ } } }
    fun invalidate(id: String) { scope.launch { mutex.withLock { cache.remove(id) } } }
}
