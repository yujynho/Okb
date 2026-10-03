package com.example.network.debrid

import android.util.Log
import com.example.network.NetworkClient
import com.example.network.await
import com.example.network.torrent.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

class RealDebridProvider(private val apiKeyProvider: () -> String) : DebridProvider {
    override val name: String = "Real-Debrid"
    private val tag = "RealDebridProvider"
    private val baseUrl = "https://api.real-debrid.com/rest/1.0"

    override fun isConfigured(): Boolean = apiKeyProvider().trim().isNotEmpty()

    override suspend fun checkCache(hash: String): CacheState = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return@withContext CacheState.UNKNOWN

        val infoHash = MagnetParser.parseHash(hash)?.lowercase() ?: return@withContext CacheState.UNKNOWN

        try {
            val url = "$baseUrl/torrents/instantAvailability/$infoHash"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .build()

            val response = NetworkClient.apiClient.newCall(request).await()
            val body = response.body?.string().orEmpty()
            val code = response.code
            response.close()

            if (code in 200..299 && body.isNotEmpty()) {
                val json = JSONObject(body)
                val hashJson = json.optJSONObject(infoHash)
                if (hashJson != null) {
                    val rdArr = hashJson.optJSONArray("rd")
                    if (rdArr != null && rdArr.length() > 0) {
                        return@withContext CacheState.CACHED
                    }
                }
            }
            return@withContext CacheState.NOT_CACHED
        } catch (e: Exception) {
            Log.e(tag, "Real-Debrid checkCache failed: ${e.message}")
        }
        CacheState.UNKNOWN
    }

    override suspend fun resolveStream(magnetOrQuery: String, allowUncached: Boolean): DebridResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) {
            return@withContext DebridResult.Error(
                type = DebridErrorType.InvalidKey,
                message = "Real-Debrid API key is missing. Please configure it in Settings.",
                providerName = name
            )
        }

        val trimmed = magnetOrQuery.trim()
        val infoHash = MagnetParser.parseHash(trimmed)
        val isMagnetOrHash = infoHash != null

        if (!isMagnetOrHash) {
            // Direct hoster link (e.g. 1fichier, rapidgator)
            return@withContext unrestrictLink(trimmed, key, null)
        }

        val canonicalMagnet = MagnetParser.toCanonicalMagnet(infoHash!!)
        var addedTorrentId: String? = null
        var isExisting = false

        try {
            Log.d(tag, "[REAL-DEBRID] Resolving infoHash: ${MagnetParser.redact(infoHash)}")

            // Step 1: Check existing active/downloaded torrents to avoid duplicate magnet additions
            val existingId = findExistingTorrentId(infoHash, key)
            if (!existingId.isNullOrEmpty()) {
                Log.d(tag, "[REAL-DEBRID] Found existing torrent with ID: $existingId")
                addedTorrentId = existingId
                isExisting = true
            } else {
                // Step 2: Add magnet to Real-Debrid
                val addBody = FormBody.Builder().add("magnet", canonicalMagnet).build()
                val addReq = Request.Builder()
                    .url("$baseUrl/torrents/addMagnet")
                    .header("Authorization", "Bearer $key")
                    .post(addBody)
                    .build()

                val addResp = NetworkClient.apiClient.newCall(addReq).await()
                val addStr = addResp.body?.string().orEmpty()
                val addCode = addResp.code
                addResp.close()

                if (addCode in listOf(401, 403)) {
                    return@withContext DebridResult.Error(
                        type = DebridErrorType.InvalidKey,
                        message = "Real-Debrid API key is invalid or unauthorized.",
                        statusCode = addCode,
                        providerName = name
                    )
                }

                val addJson = try { JSONObject(addStr) } catch (_: Exception) { JSONObject() }
                val errorMsg = addJson.optString("error", "")
                if (errorMsg.contains("bad_token", ignoreCase = true)) {
                    return@withContext DebridResult.Error(
                        type = DebridErrorType.InvalidKey,
                        message = "Invalid Real-Debrid API token.",
                        statusCode = addCode,
                        providerName = name
                    )
                } else if (errorMsg.contains("infringing", ignoreCase = true)) {
                    return@withContext DebridResult.Error(
                        type = DebridErrorType.Infringing,
                        message = "This torrent is flagged as copyright infringing on Real-Debrid.",
                        statusCode = addCode,
                        providerName = name
                    )
                }

                addedTorrentId = addJson.optString("id", "")
                if (addedTorrentId.isEmpty()) {
                    return@withContext DebridResult.Error(
                        type = DebridErrorType.Unknown,
                        message = "Real-Debrid failed to add torrent: $addStr",
                        statusCode = addCode,
                        providerName = name
                    )
                }
            }

            // Step 3: Polling loop with a 25s deadline and incremental backoff
            val startTime = System.currentTimeMillis()
            val totalTimeoutMs = 25_000L
            var pollDelayMs = 250L
            var selectedFileId: String? = null
            var filesSelected = false
            var selectedFileIndexInList = 0
            var rawLink: String? = null
            var targetFilename: String? = null
            var targetFilesize: Long? = null

            while (System.currentTimeMillis() - startTime < totalTimeoutMs) {
                val infoReq = Request.Builder()
                    .url("$baseUrl/torrents/info/$addedTorrentId")
                    .header("Authorization", "Bearer $key")
                    .build()

                val infoResp = NetworkClient.apiClient.newCall(infoReq).await()
                val infoStr = infoResp.body?.string().orEmpty()
                val infoCode = infoResp.code
                infoResp.close()

                if (infoCode != 200 || infoStr.isEmpty()) continue

                val infoJson = JSONObject(infoStr)
                val status = infoJson.optString("status")

                // Handle RD Error Statuses
                if (status in listOf("magnet_error", "error", "virus", "dead")) {
                    return@withContext DebridResult.Error(
                        type = DebridErrorType.Unknown,
                        message = "Real-Debrid torrent status error: $status",
                        statusCode = infoCode,
                        providerName = name
                    )
                }

                // Handle File Selection
                if (!filesSelected && status.equals("waiting_files_selection", ignoreCase = true)) {
                    val filesArr = infoJson.optJSONArray("files")
                    val fileList = mutableListOf<DebridFileInfo>()
                    var bestFile: DebridFileInfo? = null

                    if (filesArr != null) {
                        for (i in 0 until filesArr.length()) {
                            val fObj = filesArr.optJSONObject(i) ?: continue
                            val fId = fObj.opt("id")?.toString() ?: (i + 1).toString()
                            val fPath = fObj.optString("path", "")
                            val fBytes = fObj.optLong("bytes", 0L)
                            val isSelected = fObj.optInt("selected", 0) == 1
                            if (isSelected) {
                                fileList.add(DebridFileInfo(fId, fPath, fBytes))
                            }
                        }
                    }

                    bestFile = VideoFilePicker.pickBestVideoFile(fileList)
                    val selectParam = bestFile?.id ?: "all"
                    selectedFileId = bestFile?.id
                    targetFilename = bestFile?.name
                    targetFilesize = bestFile?.sizeBytes

                    val selectBody = FormBody.Builder().add("files", selectParam).build()
                    val selectReq = Request.Builder()
                        .url("$baseUrl/torrents/selectFiles/$addedTorrentId")
                        .header("Authorization", "Bearer $key")
                        .post(selectBody)
                        .build()

                    val selResp = NetworkClient.apiClient.newCall(selectReq).await()
                    val selCode = selResp.code
                    selResp.close()

                    if (selCode !in 200..299) {
                        return@withContext DebridResult.Error(
                            type = DebridErrorType.Unknown,
                            message = "Real-Debrid failed to select file ID: $selectParam (HTTP $selCode)",
                            statusCode = selCode,
                            providerName = name
                        )
                    }
                    filesSelected = true
                    continue
                }

                // Check Downloaded / Ready Links
                if (status.equals("downloaded", ignoreCase = true)) {
                    val linksArr = infoJson.optJSONArray("links")
                    if (linksArr != null && linksArr.length() > 0) {
                        // Find file index among selected files
                        val filesArr = infoJson.optJSONArray("files")
                        var linkIndex = 0
                        if (filesArr != null && selectedFileId != null) {
                            var selCount = 0
                            for (i in 0 until filesArr.length()) {
                                val fObj = filesArr.optJSONObject(i) ?: continue
                                val fId = fObj.opt("id")?.toString() ?: (i + 1).toString()
                                if (fObj.optInt("selected", 0) == 1) {
                                    if (fId == selectedFileId) {
                                        linkIndex = selCount
                                        break
                                    }
                                    selCount++
                                }
                            }
                        }
                        rawLink = linksArr.optString(linkIndex.coerceAtMost(linksArr.length() - 1))
                        if (targetFilename.isNullOrEmpty()) {
                            targetFilename = infoJson.optString("filename", "RealDebrid_Stream")
                        }
                        break
                    }
                }

                // Handling Uncached Downloads
                if (status in listOf("downloading", "queued", "compressing", "uploading")) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (!allowUncached && elapsedTime > 6000L) {
                        // Cleanup torrent only if we added it in this session
                        if (!isExisting) {
                            deleteTorrent(addedTorrentId, key)
                        }
                        return@withContext DebridResult.Error(
                            type = DebridErrorType.NotCached,
                            message = "Torrent is not cached on Real-Debrid.",
                            providerName = name
                        )
                    }
                }

                delay(pollDelayMs)
                pollDelayMs = (pollDelayMs + 150L).coerceAtMost(1000L)
            }

            if (rawLink.isNullOrEmpty()) {
                if (!allowUncached && !isExisting) {
                    deleteTorrent(addedTorrentId, key)
                }
                return@withContext DebridResult.Error(
                    type = DebridErrorType.NotCached,
                    message = "Real-Debrid stream link not ready or torrent is not cached.",
                    providerName = name
                )
            }

            return@withContext unrestrictLink(rawLink, key, targetFilename, targetFilesize)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                // Non-cancellable cleanup on coroutine cancellation
                withContext(NonCancellable) {
                    if (!isExisting) {
                        deleteTorrent(addedTorrentId, key)
                    }
                }
                throw e
            }
            Log.e(tag, "Real-Debrid resolveStream exception", e)
            return@withContext DebridResult.Error(
                type = DebridErrorType.Unknown,
                message = "Real-Debrid error: ${e.message}",
                providerName = name
            )
        }
    }

    private suspend fun findExistingTorrentId(infoHash: String, key: String): String? {
        try {
            val req = Request.Builder()
                .url("$baseUrl/torrents?limit=100")
                .header("Authorization", "Bearer $key")
                .build()

            val resp = NetworkClient.apiClient.newCall(req).await()
            val body = resp.body?.string().orEmpty()
            resp.close()

            if (resp.isSuccessful && body.isNotEmpty()) {
                val arr = org.json.JSONArray(body)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val hash = item.optString("hash", "").lowercase()
                    if (hash == infoHash.lowercase()) {
                        val status = item.optString("status", "")
                        if (status in listOf("downloaded", "waiting_files_selection", "downloading", "queued")) {
                            return item.optString("id", "")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "findExistingTorrentId check failed: ${e.message}")
        }
        return null
    }

    private suspend fun deleteTorrent(torrentId: String?, key: String) {
        if (torrentId.isNullOrEmpty()) return
        try {
            val req = Request.Builder()
                .url("$baseUrl/torrents/delete/$torrentId")
                .header("Authorization", "Bearer $key")
                .delete()
                .build()
            val resp = NetworkClient.apiClient.newCall(req).await()
            resp.close()
            Log.d(tag, "[REAL-DEBRID] Deleted uncached torrent ID: $torrentId")
        } catch (_: Exception) {}
    }

    private suspend fun unrestrictLink(
        rawLink: String,
        key: String,
        initialFilename: String?,
        sizeBytesHint: Long? = null
    ): DebridResult {
        Log.d(tag, "[REAL-DEBRID] Unrestricting link...")
        val form = FormBody.Builder().add("link", rawLink).build()
        val unrestrictReq = Request.Builder()
            .url("$baseUrl/unrestrict/link")
            .header("Authorization", "Bearer $key")
            .post(form)
            .build()

        val resp = NetworkClient.apiClient.newCall(unrestrictReq).await()
        val bodyStr = resp.body?.string().orEmpty()
        val code = resp.code
        resp.close()

        if (code !in 200..299) {
            return DebridResult.Error(
                type = if (code in listOf(401, 403)) DebridErrorType.InvalidKey else DebridErrorType.Unknown,
                message = "Real-Debrid unrestrict failed (HTTP $code): $bodyStr",
                statusCode = code,
                providerName = name
            )
        }

        val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }
        val downloadUrl = json.optString("download", "")
        val filename = json.optString("filename", initialFilename ?: "RealDebrid_Stream")
        val filesize = json.optLong("filesize", sizeBytesHint ?: 0L)

        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            return DebridResult.Success(
                streamUrl = downloadUrl,
                filename = filename,
                sizeBytes = if (filesize > 0) filesize else null,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                ),
                providerName = name
            )
        } else {
            return DebridResult.Error(
                type = DebridErrorType.Unknown,
                message = "Real-Debrid returned an invalid download URL: $bodyStr",
                statusCode = code,
                providerName = name
            )
        }
    }
}
