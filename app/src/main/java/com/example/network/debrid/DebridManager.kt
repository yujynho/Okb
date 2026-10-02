package com.example.network.debrid

import android.util.Log
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

        val cacheKey = queryOrMagnet.trim().lowercase()
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
                // AUTO: Check Torbox cache first (zero side-effects)
                val torboxCache = torboxProvider.checkCache(queryOrMagnet)
                if (torboxCache == CacheState.CACHED) {
                    val torRes = torboxProvider.resolveStream(queryOrMagnet, allowUncached)
                    if (torRes is DebridResult.Success) return@coroutineScope torRes
                }

                // Try Real-Debrid
                val rdRes = realDebridProvider.resolveStream(queryOrMagnet, allowUncached)
                if (rdRes is DebridResult.Success) return@coroutineScope rdRes

                // Fallback to Torbox
                val torRes = torboxProvider.resolveStream(queryOrMagnet, allowUncached)
                if (torRes is DebridResult.Success) return@coroutineScope torRes

                val errorMsg = "Resolution failed. RD: ${(rdRes as? DebridResult.Error)?.message} | Torbox: ${(torRes as? DebridResult.Error)?.message}"
                return@coroutineScope DebridResult.Error(
                    type = (rdRes as? DebridResult.Error)?.type ?: DebridErrorType.Unknown,
                    message = errorMsg,
                    providerName = "Debrid"
                )
            }
        }
    }
}
