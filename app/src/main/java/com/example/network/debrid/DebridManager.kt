package com.example.network.debrid

import android.util.Log
import com.example.network.torrent.MagnetParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

enum class DebridOrder {
    AUTO,
    REAL_DEBRID_FIRST,
    TORBOX_FIRST
}

data class CachedStream(
    val result: DebridResult.Success,
    val timestampMs: Long = System.currentTimeMillis()
)

class DebridManager(
    private val torboxKeyProvider: () -> String,
    private val realDebridKeyProvider: () -> String,
    private val debridOrderProvider: () -> DebridOrder = { DebridOrder.AUTO },
    private val allowUncachedProvider: () -> Boolean = { false }
) {
    private val tag = "DebridManager"

    val torboxProvider = TorboxProvider(torboxKeyProvider)
    val realDebridProvider = RealDebridProvider(realDebridKeyProvider)

    companion object {
        private val resolvedStreamCache = ConcurrentHashMap<String, CachedStream>()
        private const val CACHE_TTL_MS = 20 * 60 * 1000L // 20 minutes

        fun invalidateCache(key: String?) {
            if (!key.isNullOrBlank()) {
                resolvedStreamCache.remove(key.lowercase().trim())
            }
        }
    }

    fun hasAnyProviderConfigured(): Boolean {
        return torboxProvider.isConfigured() || realDebridProvider.isConfigured()
    }

    suspend fun resolve(queryOrMagnet: String): DebridResult = coroutineScope {
        val torboxConfigured = torboxProvider.isConfigured()
        val rdConfigured = realDebridProvider.isConfigured()
        val allowUncached = allowUncachedProvider()
        val order = debridOrderProvider()

        if (!torboxConfigured && !rdConfigured) {
            return@coroutineScope DebridResult.Error(
                type = DebridErrorType.InvalidKey,
                message = "No Debrid provider is configured. Please add your API key in Settings.",
                providerName = "None"
            )
        }

        val infoHash = MagnetParser.parseHash(queryOrMagnet)
        val cacheKey = infoHash?.lowercase() ?: queryOrMagnet.trim().lowercase()
        val cached = resolvedStreamCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestampMs < CACHE_TTL_MS)) {
            Log.d(tag, "Returning cached Debrid stream for: $cacheKey")
            return@coroutineScope cached.result
        }

        val result = executeResolution(queryOrMagnet, torboxConfigured, rdConfigured, order, allowUncached)

        if (result is DebridResult.Success) {
            resolvedStreamCache[cacheKey] = CachedStream(result)
        }

        return@coroutineScope result
    }

    private suspend fun executeResolution(
        queryOrMagnet: String,
        torboxConfigured: Boolean,
        rdConfigured: Boolean,
        order: DebridOrder,
        allowUncached: Boolean
    ): DebridResult = coroutineScope {
        if (torboxConfigured && !rdConfigured) {
            return@coroutineScope torboxProvider.resolveStream(queryOrMagnet, allowUncached)
        }
        if (!torboxConfigured && rdConfigured) {
            return@coroutineScope realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
        }

        when (order) {
            DebridOrder.REAL_DEBRID_FIRST -> {
                val rdRes = realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
                if (rdRes is DebridResult.Success) return@coroutineScope rdRes
                return@coroutineScope torboxProvider.resolveStream(queryOrMagnet, allowUncached)
            }
            DebridOrder.TORBOX_FIRST -> {
                val torRes = torboxProvider.resolveStream(queryOrMagnet, allowUncached)
                if (torRes is DebridResult.Success) return@coroutineScope torRes
                return@coroutineScope realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
            }
            DebridOrder.AUTO -> {
                // Check both caches concurrently in parallel to cut check time in half!
                val torboxCacheDeferred = async { torboxProvider.checkCache(queryOrMagnet) }
                val rdCacheDeferred = async { realDebridProvider.checkCache(queryOrMagnet) }

                val torboxCache = torboxCacheDeferred.await()
                val rdCache = rdCacheDeferred.await()

                if (torboxCache == CacheState.CACHED) {
                    val torRes = torboxProvider.resolveStream(queryOrMagnet, allowUncached)
                    if (torRes is DebridResult.Success) return@coroutineScope torRes
                }

                if (rdCache == CacheState.CACHED) {
                    val rdRes = realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
                    if (rdRes is DebridResult.Success) return@coroutineScope rdRes
                }

                // 3. Fallback: resolve via Real-Debrid first due to superior torrent speeds
                val rdRes = realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
                if (rdRes is DebridResult.Success) return@coroutineScope rdRes

                // Only attempt Torbox resolution if we didn't already verify that it is NOT_CACHED
                if (torboxCache != CacheState.NOT_CACHED || allowUncached) {
                    val torRes = torboxProvider.resolveStream(queryOrMagnet, allowUncached)
                    if (torRes is DebridResult.Success) return@coroutineScope torRes
                }

                val errorMsg = "Resolution failed. RD: ${(rdRes as? DebridResult.Error)?.message}"
                return@coroutineScope DebridResult.Error(
                    type = (rdRes as? DebridResult.Error)?.type ?: DebridErrorType.Unknown,
                    message = errorMsg,
                    providerName = "Debrid"
                )
            }
        }
    }
}
