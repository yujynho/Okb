package com.example.network.torrent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.local.entity.StudioEntity
import com.example.network.NetworkClient
import com.example.network.StashDbApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

object TorrentScraper {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Max 4 concurrent requests per host (Section 7)
    private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()

    private fun getHostSemaphore(host: String): Semaphore {
        return hostSemaphores.getOrPut(host) { Semaphore(4) }
    }

    /**
     * Fetch HTML with browser headers, Cloudflare check, and hidden WebView fallback.
     */
    suspend fun fetchHtml(
        urlStr: String,
        timeoutMs: Long = 3500,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val uri = try {
            URI(urlStr)
        } catch (_: Exception) {
            null
        }
        val host = uri?.host ?: "default"
        val sem = getHostSemaphore(host)

        sem.withPermit {
            // Direct GET with browser headers (Section 7)
            try {
                val reqBuilder = Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")

                val client = NetworkClient.okHttpClient.newBuilder()
                    .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()

                val response = client.newCall(reqBuilder.build()).execute()
                val body = response.body?.string() ?: ""

                val isCfBlocked = body.contains("Just a moment...", ignoreCase = true) ||
                        body.contains("cf-browser-verification", ignoreCase = true)

                if (response.isSuccessful && !isCfBlocked) {
                    return@withPermit body
                }

                // If Cloudflare blocked or error status and context is available, attempt WebView fallback
                if (context != null && (isCfBlocked || response.code == 403 || response.code == 503)) {
                    val webViewHtml = fetchHtmlWithWebView(urlStr, context)
                    if (webViewHtml.isNotBlank()) {
                        return@withPermit webViewHtml
                    }
                }

                if (isCfBlocked) {
                    throw Exception("Cloudflare verification active on $host")
                } else if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                body
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // If direct network failed and context is provided, try WebView fallback as last resort
                if (context != null) {
                    try {
                        val webViewHtml = fetchHtmlWithWebView(urlStr, context)
                        if (webViewHtml.isNotBlank()) {
                            return@withPermit webViewHtml
                        }
                    } catch (we: Exception) {
                        if (we is CancellationException) throw we
                    }
                }
                throw Exception("Failed to fetch $host page ($urlStr): ${e.message}")
            }
        }
    }

    /**
     * Fallback: Load URL in hidden WebView to bypass Cloudflare challenge (Section 7).
     */
    private suspend fun fetchHtmlWithWebView(url: String, context: Context): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val webView = WebView(context.applicationContext)
                    val settings: WebSettings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = BROWSER_USER_AGENT

                    val handler = Handler(Looper.getMainLooper())
                    var isCompleted = false

                    val timeoutRunnable = Runnable {
                        if (!isCompleted) {
                            isCompleted = true
                            try {
                                webView.stopLoading()
                                webView.destroy()
                            } catch (_: Exception) {}
                            continuation.resume("")
                        }
                    }
                    handler.postDelayed(timeoutRunnable, 8000)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            val title = view?.title ?: ""
                            if (!title.contains("Just a moment...", ignoreCase = true) &&
                                !title.contains("cf-browser-verification", ignoreCase = true) &&
                                !isCompleted
                            ) {
                                isCompleted = true
                                handler.removeCallbacks(timeoutRunnable)

                                // Copy cookies to OkHttp
                                try {
                                    val cookieManager = CookieManager.getInstance()
                                    val cookieStr = cookieManager.getCookie(loadedUrl ?: url)
                                    if (!cookieStr.isNullOrBlank()) {
                                        val httpUrl = (loadedUrl ?: url).toHttpUrlOrNull()
                                        if (httpUrl != null) {
                                            val cookiesList = cookieStr.split(";").mapNotNull {
                                                Cookie.parse(httpUrl, it.trim())
                                            }
                                            NetworkClient.okHttpClient.cookieJar.saveFromResponse(httpUrl, cookiesList)
                                        }
                                    }
                                } catch (_: Exception) {}

                                view?.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML; })();"
                                ) { htmlJson ->
                                    try {
                                        view.destroy()
                                    } catch (_: Exception) {}
                                    val rawHtml = if (htmlJson != null && htmlJson.startsWith("\"") && htmlJson.endsWith("\"")) {
                                        try {
                                            org.json.JSONTokener(htmlJson).nextValue().toString()
                                        } catch (_: Exception) {
                                            htmlJson
                                        }
                                    } else {
                                        htmlJson ?: ""
                                    }
                                    continuation.resume(rawHtml)
                                }
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        handler.removeCallbacks(timeoutRunnable)
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {}
                    }

                    webView.loadUrl(url)
                } catch (e: Exception) {
                    continuation.resume("")
                }
            }
        }

    // ==========================================
    // 1. DIRECT EXTRACT SHORTCUT (Section 3 & 5.5)
    // ==========================================
    suspend fun directExtract(url: String, context: Context? = null): TorrentSearchResult? {
        val detailUrl = url.trim()
        if (!detailUrl.contains("xxxclub.to/torrents/details/")) return null
        return try {
            val html = fetchHtml(detailUrl, timeoutMs = 4500, context = context)
            val parsed = parseXxxclubDetail(html, detailUrl) ?: return null
            if (parsed.magnet2160p != null) {
                TorrentSearchResult(
                    magnet2160p = parsed.magnet2160p,
                    url2160p = detailUrl,
                    sourceSite = "xxxclub.to (Direct Extract)"
                )
            } else if (parsed.magnet1080p != null) {
                TorrentSearchResult(
                    magnet1080p = parsed.magnet1080p,
                    url1080p = detailUrl,
                    sourceSite = "xxxclub.to (Direct Extract)"
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // 2. SUKEBEI SEARCH (JAV) (Section 4)
    // ==========================================
    fun parseSukebei(html: String, javCode: String): TorrentSearchResult {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr")

        val normalizedJav = javCode.lowercase().replace(Regex("[-_\\s]"), "")

        var fallbackRow: SukebeiRow? = null
        var k4Row: SukebeiRow? = null
        var fhd1080Row: SukebeiRow? = null
        var hdBareRow: SukebeiRow? = null

        for (tr in rows) {
            val titleElem = tr.select("td[colspan=\"2\"] a:not(.comments)").first()
                ?: tr.select("td a[title]").first()
                ?: continue

            val title = titleElem.text().ifBlank { titleElem.attr("title") }.trim()
            val magnetElem = tr.select("td a[href^=\"magnet:\"]").first() ?: continue
            val magnet = magnetElem.attr("href").trim()
            val viewHref = titleElem.attr("href")
            val viewUrl = if (viewHref.startsWith("http")) viewHref else "https://sukebei.nyaa.si$viewHref"

            if (title.isBlank() || magnet.isBlank()) continue
            if (!MagnetUtils.isValidMagnetUri(magnet)) continue

            // [IMPROVEMENT] (a): Normalized title must contain normalized JAV code
            val normTitle = title.lowercase().replace(Regex("[-_\\s]"), "")
            if (!normTitle.contains(normalizedJav)) continue

            // [IMPROVEMENT] (b): VP9 exclusion before choosing fallback
            val upperTitle = title.uppercase()
            if (upperTitle.contains("VP9")) continue

            val row = SukebeiRow(title, magnet, viewUrl)

            if (fallbackRow == null) {
                fallbackRow = row
            }

            if (k4Row == null && (upperTitle.contains("4K") || upperTitle.contains("2160P"))) {
                k4Row = row
            }

            // [IMPROVEMENT] (c): Give FHD/1080P priority over bare HD
            if (fhd1080Row == null && (upperTitle.contains("FHD") || upperTitle.contains("1080P"))) {
                fhd1080Row = row
            } else if (hdBareRow == null && upperTitle.contains("HD")) {
                hdBareRow = row
            }
        }

        val hdRow = fhd1080Row ?: hdBareRow ?: fallbackRow

        return TorrentSearchResult(
            magnet1080p = hdRow?.magnet,
            url1080p = hdRow?.viewUrl,
            magnet2160p = k4Row?.magnet,
            url2160p = k4Row?.viewUrl,
            sourceSite = "sukebei.nyaa.si"
        )
    }

    private data class SukebeiRow(val title: String, val magnet: String, val viewUrl: String)

    suspend fun searchSukebei(javCode: String, context: Context? = null): TorrentSearchResult {
        val encodedQuery = URLEncoder.encode(javCode.trim(), "UTF-8")
        val url = "https://sukebei.nyaa.si/?f=0&c=0_0&q=$encodedQuery&s=downloads&o=desc"
        return try {
            val html = fetchHtml(url, timeoutMs = 4000, context = context)
            parseSukebei(html, javCode)
        } catch (e: Exception) {
            throw Exception("Sukebei blocked the request (Cloudflare) or failed: ${e.message}")
        }
    }

    // ==========================================
    // 3. XXXCLUB SMART SEARCH (WESTERN SCENES) (Section 5)
    // ==========================================

    fun parseXxxclubList(
        html: String,
        query: String,
        datePatterns: Set<String>,
        studios: List<String>,
        actors: List<String>
    ): List<TorrentCandidate> {
        val doc = Jsoup.parse(html)
        val lis = doc.select("ul.tsearch > li")
        val candidates = mutableListOf<TorrentCandidate>()

        for (li in lis) {
            val titleElem = li.select("a[href^=\"/torrents/details/\"], a[href*=\"/torrents/details/\"]").first() ?: continue
            val title = titleElem.text().trim()
            val href = titleElem.attr("href").trim()
            val absoluteUrl = if (href.startsWith("http")) href else "https://xxxclub.to$href"

            val magnetElem = li.select("a[href^=\"magnet:\"]").first()
            val embeddedMagnet = magnetElem?.attr("href")?.trim()?.ifBlank { null }
            if (embeddedMagnet != null && !MagnetUtils.isValidMagnetUri(embeddedMagnet)) {
                // If embedded magnet is invalid, disregard it
                continue
            }

            if (title.isBlank() || href.isBlank()) continue

            val lowerTitle = title.lowercase()

            // 1. VP9 check
            if (lowerTitle.contains("vp9")) continue

            // 2. Strict date patterns check
            if (datePatterns.isNotEmpty() && !DatePatterns.matchesAnyDatePattern(title, datePatterns)) {
                continue
            }

            // 3. SD / low quality rejection: contains 480p, 720p, " sd ", "-sd", or matches \bsd\b
            if (lowerTitle.contains("480p") || lowerTitle.contains("720p") ||
                lowerTitle.contains(" sd ") || lowerTitle.contains("-sd") ||
                Pattern.compile("\\bsd\\b").matcher(lowerTitle).find()
            ) {
                continue
            }

            // 4. Quality detection: is4k and is1080p
            val is4k = lowerTitle.contains("2160p") || lowerTitle.contains("4k") || lowerTitle.contains("uhd")
            val is1080p = lowerTitle.contains("1080p") || lowerTitle.contains("fullhd") ||
                    lowerTitle.contains("full hd") || lowerTitle.contains("1080")

            if (!is4k && !is1080p) continue

            // 5. Anchor check
            val anchorResult = AnchorMatcher.checkAnchors(title, studios, actors, query)
            if (!anchorResult.isMatch) continue

            // 6. Score
            val score = AnchorMatcher.calculateScore(title, query, anchorResult.studioMatched, anchorResult.actorMatched)

            candidates.add(
                TorrentCandidate(
                    title = title,
                    detailUrl = absoluteUrl,
                    embeddedMagnet = embeddedMagnet,
                    score = score,
                    is4k = is4k,
                    is1080p = is1080p
                )
            )
        }

        return candidates
    }

    /**
     * Honeypot-safe next page link detection (Section 5.8).
     */
    fun findNextPageUrl(html: String): String? {
        val doc = Jsoup.parse(html)
        val links = doc.select("a")

        for (a in links) {
            val text = a.text().trim().lowercase()
            val titleAttr = a.attr("title").trim()
            val style = a.attr("style").lowercase()

            val isNextText = text.contains("next page") || text == "next" || titleAttr.equals("Next Page", ignoreCase = true)
            if (!isNextText) continue

            // Check honeypot indicators
            if (style.contains("-9999px") || style.contains("display:none") || style.contains("visibility:hidden")) {
                continue
            }

            val href = a.attr("href").trim()
            if (href.isNotBlank()) {
                return if (href.startsWith("http")) href else "https://xxxclub.to$href"
            }
        }
        return null
    }

    private val DETAIL_MAGNET_PATTERN = Pattern.compile("magnet:\\?xt=urn:btih:[^\"'\\s<>]+")
    private val DETAIL_QUALITY_PATTERN = Pattern.compile("(?i)(\\d{3,4}p|4K|8K|2160p)")

    /**
     * Parses detail page for magnet and quality (Section 5.5).
     */
    fun parseXxxclubDetail(html: String, detailUrl: String): TorrentSearchResult? {
        val matcher = DETAIL_MAGNET_PATTERN.matcher(html)
        val magnet = if (matcher.find()) matcher.group(0) else null
        if (magnet == null || !MagnetUtils.isValidMagnetUri(magnet)) return null

        val doc = Jsoup.parse(html)
        val titleText = doc.title().replace(Regex("(?i)\\s*-\\s*XXXCLUB.*"), "").ifBlank {
            doc.select("h1").text()
        }

        val qMatcher = DETAIL_QUALITY_PATTERN.matcher(titleText)
        val quality = if (qMatcher.find()) qMatcher.group(1).lowercase() else "1080p"
        val is4k = quality.contains("2160p") || quality.contains("4k") || quality.contains("8k")

        return if (is4k) {
            TorrentSearchResult(magnet2160p = magnet, url2160p = detailUrl, sourceSite = "xxxclub.to")
        } else {
            TorrentSearchResult(magnet1080p = magnet, url1080p = detailUrl, sourceSite = "xxxclub.to")
        }
    }

    data class QueryCandidates(
        var best1080: TorrentCandidate? = null,
        var best4k: TorrentCandidate? = null,
        var fallback: TorrentCandidate? = null
    ) {
        fun evaluate(candidates: List<TorrentCandidate>) {
            for (c in candidates) {
                if (fallback == null || c.score > fallback!!.score) {
                    fallback = c
                }
                if (c.is4k) {
                    if (best4k == null || c.score > best4k!!.score) {
                        best4k = c
                    }
                } else if (c.is1080p) {
                    if (best1080 == null || c.score > best1080!!.score) {
                        best1080 = c
                    }
                }
            }
        }

        fun finalizeCandidates() {
            if (best1080 == null && best4k == null && fallback != null) {
                best1080 = fallback
            }
        }
    }

    /**
     * Executes single query search across category 2, 4, pagination, and all fallback (Section 5.8).
     */
    suspend fun searchSingleQuery(
        query: String,
        datePatterns: Set<String>,
        studios: List<String>,
        actors: List<String>,
        context: Context? = null
    ): QueryCandidates = coroutineScope {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val qc = QueryCandidates()

        // 1. Fetch category 2 and category 4 in parallel
        val cat2Deferred = async {
            try {
                fetchHtml("https://xxxclub.to/torrents/search/2/$encodedQ", timeoutMs = 3500, context = context)
            } catch (_: Exception) { "" }
        }
        val cat4Deferred = async {
            try {
                fetchHtml("https://xxxclub.to/torrents/search/4/$encodedQ", timeoutMs = 3500, context = context)
            } catch (_: Exception) { "" }
        }

        val cat2Html = cat2Deferred.await()
        val cat4Html = cat4Deferred.await()

        if (cat2Html.isNotBlank()) {
            qc.evaluate(parseXxxclubList(cat2Html, query, datePatterns, studios, actors))
        }
        if (cat4Html.isNotBlank()) {
            qc.evaluate(parseXxxclubList(cat4Html, query, datePatterns, studios, actors))
        }

        // 2. Pagination if missing
        val next2Url = if (qc.best1080 == null && cat2Html.isNotBlank()) findNextPageUrl(cat2Html) else null
        val next4Url = if (qc.best4k == null && cat4Html.isNotBlank()) findNextPageUrl(cat4Html) else null

        if (next2Url != null || next4Url != null) {
            val next2Def = async {
                if (next2Url != null) {
                    try { fetchHtml(next2Url, timeoutMs = 3500, context = context) } catch (_: Exception) { "" }
                } else ""
            }
            val next4Def = async {
                if (next4Url != null) {
                    try { fetchHtml(next4Url, timeoutMs = 3500, context = context) } catch (_: Exception) { "" }
                } else ""
            }

            val next2Html = next2Def.await()
            val next4Html = next4Def.await()
            if (next2Html.isNotBlank()) {
                qc.evaluate(parseXxxclubList(next2Html, query, datePatterns, studios, actors))
            }
            if (next4Html.isNotBlank()) {
                qc.evaluate(parseXxxclubList(next4Html, query, datePatterns, studios, actors))
            }
        }

        // 3. Fallback to /search/all if either candidate missing
        if (qc.best1080 == null || qc.best4k == null) {
            val allHtml = try {
                fetchHtml("https://xxxclub.to/torrents/search/all/$encodedQ", timeoutMs = 3500, context = context)
            } catch (_: Exception) { "" }

            if (allHtml.isNotBlank()) {
                qc.evaluate(parseXxxclubList(allHtml, query, datePatterns, studios, actors))

                if (qc.best1080 == null || qc.best4k == null) {
                    val allNextUrl = findNextPageUrl(allHtml)
                    if (allNextUrl != null) {
                        val allNextHtml = try {
                            fetchHtml(allNextUrl, timeoutMs = 3500, context = context)
                        } catch (_: Exception) { "" }
                        if (allNextHtml.isNotBlank()) {
                            qc.evaluate(parseXxxclubList(allNextHtml, query, datePatterns, studios, actors))
                        }
                    }
                }
            }
        }

        qc.finalizeCandidates()
        qc
    }

    /**
     * Executes queries loop in batches of 2 in parallel (Section 5.9).
     */
    suspend fun executeQueriesLoop(
        queries: List<String>,
        datePatterns: Set<String>,
        studios: List<String>,
        actors: List<String>,
        context: Context? = null
    ): TorrentSearchResult? = coroutineScope {
        for (i in queries.indices step 2) {
            ensureActive()
            val batch = queries.subList(i, minOf(i + 2, queries.size))

            val candidateResults = batch.map { q ->
                async {
                    ensureActive()
                    try {
                        val qc = searchSingleQuery(q, datePatterns, studios, actors, context)
                        q to qc
                    } catch (_: Exception) {
                        q to null
                    }
                }
            }.awaitAll()

            for ((query, qc) in candidateResults) {
                ensureActive()
                if (qc == null) continue

                var mag1080 = qc.best1080?.embeddedMagnet
                var mag4k = qc.best4k?.embeddedMagnet
                val url1080 = qc.best1080?.detailUrl
                val url4k = qc.best4k?.detailUrl

                // Fetch detail pages in parallel if embedded magnet missing
                val fetch1080Needed = mag1080 == null && url1080 != null
                val fetch4kNeeded = mag4k == null && url4k != null

                if (fetch1080Needed || fetch4kNeeded) {
                    val d1080Def = async {
                        if (fetch1080Needed && url1080 != null) {
                            try {
                                val html = fetchHtml(url1080, timeoutMs = 4000, context = context)
                                parseXxxclubDetail(html, url1080)?.magnet1080p
                            } catch (_: Exception) { null }
                        } else null
                    }
                    val d4kDef = async {
                        if (fetch4kNeeded && url4k != null) {
                            try {
                                val html = fetchHtml(url4k, timeoutMs = 4000, context = context)
                                parseXxxclubDetail(html, url4k)?.magnet2160p
                            } catch (_: Exception) { null }
                        } else null
                    }

                    if (fetch1080Needed) mag1080 = d1080Def.await()
                    if (fetch4kNeeded) mag4k = d4kDef.await()
                }

                if (MagnetUtils.isValidMagnetUri(mag1080) || MagnetUtils.isValidMagnetUri(mag4k)) {
                    return@coroutineScope TorrentSearchResult(
                        magnet1080p = mag1080?.takeIf { MagnetUtils.isValidMagnetUri(it) },
                        url1080p = url1080,
                        magnet2160p = mag4k?.takeIf { MagnetUtils.isValidMagnetUri(it) },
                        url2160p = url4k,
                        sourceSite = "xxxclub.to",
                        matchedQuery = query
                    )
                }
            }

            // Small delay between batches (100 to 300 ms)
            delay(Random.nextLong(100, 301))
        }

        null
    }

    // ==========================================
    // 4. STASHDB PARENT/CHILD FALLBACK (Section 6)
    // ==========================================
    suspend fun tryStashDbStudioFallback(
        primaryActor: String?,
        dateStr: String,
        selectedStudios: List<StudioEntity>,
        stashDbApiKey: String,
        datePatterns: Set<String>,
        actors: List<String>,
        context: Context? = null
    ): TorrentSearchResult? = coroutineScope {
        if (stashDbApiKey.isBlank() || selectedStudios.isEmpty()) return@coroutineScope null

        val altStudios = mutableListOf<String>()

        for (studio in selectedStudios) {
            ensureActive()
            try {
                val stashStudio = if (!studio.stashDbId.isNullOrBlank()) {
                    StashDbApiService.findStudio(studio.stashDbId, stashDbApiKey)
                } else {
                    val searchRes = StashDbApiService.searchStudios(studio.name, stashDbApiKey).getOrNull()
                    searchRes?.firstOrNull()
                }

                if (stashStudio?.parentName != null) {
                    val parentName = stashStudio.parentName
                    if (!altStudios.contains(parentName)) altStudios.add(parentName)

                    // Query parent sub-studios
                    val parentStudioObj = StashDbApiService.searchStudios(parentName, stashDbApiKey).getOrNull()?.firstOrNull()
                    if (parentStudioObj != null) {
                        val parentFull = StashDbApiService.findStudio(parentStudioObj.id, stashDbApiKey)
                        if (parentFull != null) {
                            for (childId in parentFull.childIds) {
                                ensureActive()
                                val childObj = StashDbApiService.findStudio(childId, stashDbApiKey)
                                val cName = childObj?.name
                                if (!cName.isNullOrBlank() &&
                                    !cName.equals(studio.name, ignoreCase = true) &&
                                    !altStudios.contains(cName)
                                ) {
                                    altStudios.add(cName)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (altStudios.isEmpty()) return@coroutineScope null

        // Build new queries: primaryActorVariant + cleanStudio(altStudio) (noSpace form) + dateStr
        val primaryVariants = if (primaryActor != null) {
            QueryBuilder.getActorVariants(primaryActor).toList()
        } else {
            emptyList()
        }

        val fallbackQueries = mutableListOf<String>()
        for (alt in altStudios) {
            val variants = StudioNameNormalizer.getStudioVariants(alt)
            val noSpaceVariant = variants.getOrNull(1) ?: variants.firstOrNull() ?: alt
            if (primaryVariants.isNotEmpty()) {
                for (pv in primaryVariants) {
                    val q = "$pv $noSpaceVariant $dateStr".replace(Regex("\\s+"), " ").trim()
                    if (!fallbackQueries.contains(q)) fallbackQueries.add(q)
                }
            } else {
                val q = "$noSpaceVariant $dateStr".replace(Regex("\\s+"), " ").trim()
                if (!fallbackQueries.contains(q)) fallbackQueries.add(q)
            }
        }

        val allStudiosCombined = (selectedStudios.map { it.name } + altStudios).distinct()
        executeQueriesLoop(fallbackQueries, datePatterns, allStudiosCombined, actors, context)
    }
}
