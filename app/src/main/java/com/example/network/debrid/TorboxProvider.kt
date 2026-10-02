package com.example.network.debrid

import android.util.Log
import com.example.network.NetworkClient
import com.example.network.await
import com.example.network.torrent.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TorboxProvider(private val apiKeyProvider: () -> String) : DebridProvider {
    override val name: String = "Torbox"
    private val tag = "TorboxProvider"
    private val baseUrl = "https://api.torbox.app/v1/api"

    override fun isConfigured(): Boolean = apiKeyProvider().trim().isNotEmpty()

    override suspend fun checkCache(hash: String): CacheState = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return@withContext CacheState.UNKNOWN

        val infoHash = MagnetParser.parseHash(hash) ?: return@withContext CacheState.UNKNOWN

        try {
            val url = "$baseUrl/torrents/checkcached?hash=$infoHash&format=object"
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
                val success = json.optBoolean("success", false)
                val data = json.optJSONObject("data")
                if (success && data != null) {
                    val torrentData = data.opt(infoHash) ?: data.opt(infoHash.uppercase())
                    if (torrentData != null && torrentData != JSONObject.NULL && torrentData != false) {
                        return@withContext CacheState.CACHED
                    }
                }
            }
            return@withContext CacheState.NOT_CACHED
        } catch (e: Exception) {
            Log.e(tag, "Torbox checkCache failed: ${e.message}")
        }
        CacheState.UNKNOWN
    }

    override suspend fun resolveStream(magnetOrQuery: String, allowUncached: Boolean): DebridResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) {
            return@withContext DebridResult.Error(
                type = DebridErrorType.InvalidKey,
                message = "Torbox API key is missing. Please configure it in Settings.",
                providerName = name
            )
        }

        val trimmed = magnetOrQuery.trim()
        val infoHash = MagnetParser.parseHash(trimmed)
        val canonicalMagnet = if (infoHash != null) MagnetParser.toCanonicalMagnet(infoHash) else trimmed

        if (infoHash != null && !allowUncached) {
            val cacheState = checkCache(infoHash)
            if (cacheState == CacheState.NOT_CACHED) {
                return@withContext DebridResult.Error(
                    type = DebridErrorType.NotCached,
                    message = "Torrent is not cached on Torbox.",
                    providerName = name
                )
            }
        }

        try {
            Log.d(tag, "[TORBOX] Resolving stream...")

            var torrentId: String? = null
            var selectedFileId: String? = null
            var filename: String? = null
            var sizeBytes: Long? = null

            // Step 1: Add torrent
            val formBody = FormBody.Builder()
                .add("magnet", canonicalMagnet)
                .add("seed", "1")
                .add("allow_zip", "false")
                .build()

            val createReq = Request.Builder()
                .url("$baseUrl/torrents/createtorrent")
                .header("Authorization", "Bearer $key")
                .post(formBody)
                .build()

            val createResp = NetworkClient.apiClient.newCall(createReq).await()
            val createBody = createResp.body?.string().orEmpty()
            val createCode = createResp.code
            createResp.close()

            if (createCode in listOf(401, 403)) {
                return@withContext DebridResult.Error(
                    type = DebridErrorType.InvalidKey,
                    message = "Torbox API key is invalid.",
                    statusCode = createCode,
                    providerName = name
                )
            }

            if (createCode in 200..299 && createBody.isNotEmpty()) {
                val createJson = JSONObject(createBody)
                if (createJson.optBoolean("success", false)) {
                    val dataObj = createJson.optJSONObject("data")
                    if (dataObj != null) {
                        torrentId = dataObj.optString("torrent_id", dataObj.optString("id", ""))
                        if (torrentId.isEmpty()) {
                            val qId = dataObj.opt("queued_id")?.toString()
                            if (!qId.isNullOrEmpty()) torrentId = qId
                        }
                    } else {
                        val simpleId = createJson.optString("torrent_id", createJson.optString("id", ""))
                        if (simpleId.isNotEmpty()) torrentId = simpleId
                    }
                }
            }

            // Fallback search in mylist if torrentId was not directly returned
            if (torrentId.isNullOrEmpty() && infoHash != null) {
                torrentId = findInMyList(infoHash, key)
            }

            if (torrentId.isNullOrEmpty()) {
                return@withContext DebridResult.Error(
                    type = DebridErrorType.Unknown,
                    message = "Unable to create or locate torrent on Torbox account.",
                    providerName = name
                )
            }

            // Step 2: Poll mylist until file list is available
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 20_000L) {
                val infoReq = Request.Builder()
                    .url("$baseUrl/torrents/mylist?id=$torrentId")
                    .header("Authorization", "Bearer $key")
                    .build()

                val infoResp = NetworkClient.apiClient.newCall(infoReq).await()
                val infoBody = infoResp.body?.string().orEmpty()
                val infoCode = infoResp.code
                infoResp.close()

                if (infoCode in 200..299 && infoBody.isNotEmpty()) {
                    val infoJson = JSONObject(infoBody)
                    val dataObj = infoJson.optJSONObject("data")
                    val filesArr = dataObj?.optJSONArray("files") ?: infoJson.optJSONArray("data")?.optJSONObject(0)?.optJSONArray("files")

                    if (filesArr != null && filesArr.length() > 0) {
                        val fileList = mutableListOf<DebridFileInfo>()
                        for (i in 0 until filesArr.length()) {
                            val fObj = filesArr.optJSONObject(i) ?: continue
                            val fId = fObj.opt("id")?.toString() ?: i.toString()
                            val fName = fObj.optString("name", fObj.optString("short_name", ""))
                            val fSize = fObj.optLong("size", fObj.optLong("bytes", 0L))
                            fileList.add(DebridFileInfo(fId, fName, fSize))
                        }

                        val bestFile = VideoFilePicker.pickBestVideoFile(fileList)
                        if (bestFile != null) {
                            selectedFileId = bestFile.id
                            filename = bestFile.name
                            sizeBytes = bestFile.sizeBytes
                            break
                        }
                    }
                }
                delay(300)
            }

            // Step 3: Request Direct Link
            val fileParam = if (!selectedFileId.isNullOrEmpty()) "&file_id=$selectedFileId" else ""
            val requestDlUrl = "$baseUrl/torrents/requestdl?token=$key&torrent_id=$torrentId$fileParam&zip_link=false"

            val dlReq = Request.Builder()
                .url(requestDlUrl)
                .header("Authorization", "Bearer $key")
                .build()

            val dlResp = NetworkClient.apiClient.newCall(dlReq).await()
            val dlBody = dlResp.body?.string().orEmpty()
            val dlCode = dlResp.code
            dlResp.close()

            if (dlCode !in 200..299) {
                return@withContext DebridResult.Error(
                    type = if (dlCode in listOf(401, 403)) DebridErrorType.InvalidKey else DebridErrorType.Unknown,
                    message = "Torbox requestdl failed (HTTP $dlCode): $dlBody",
                    statusCode = dlCode,
                    providerName = name
                )
            }

            val dlJson = JSONObject(dlBody)
            if (dlJson.optBoolean("success", false)) {
                val downloadUrl = if (dlJson.optJSONObject("data") != null) {
                    dlJson.getJSONObject("data").optString("url")
                } else {
                    dlJson.optString("data")
                }.trim()

                if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                    return@withContext DebridResult.Success(
                        streamUrl = downloadUrl,
                        filename = filename ?: "Torbox_Stream",
                        sizeBytes = sizeBytes,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        ),
                        providerName = name
                    )
                }
            }

            val detail = dlJson.optString("detail", "Torbox failed to generate stream link")
            return@withContext DebridResult.Error(
                type = DebridErrorType.Unknown,
                message = detail,
                statusCode = dlCode,
                providerName = name
            )

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(tag, "Torbox resolveStream error", e)
            return@withContext DebridResult.Error(
                type = DebridErrorType.Unknown,
                message = "Torbox resolution error: ${e.message}",
                providerName = name
            )
        }
    }

    private suspend fun findInMyList(infoHash: String, key: String): String? {
        try {
            val listUrl = "$baseUrl/torrents/mylist"
            val listReq = Request.Builder()
                .url(listUrl)
                .header("Authorization", "Bearer $key")
                .build()

            val listResp = NetworkClient.apiClient.newCall(listReq).await()
            val listBody = listResp.body?.string().orEmpty()
            listResp.close()

            if (listResp.isSuccessful && listBody.isNotEmpty()) {
                val listJson = JSONObject(listBody)
                val dataArr = listJson.optJSONArray("data")
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val itemHash = item.optString("hash", "").lowercase()
                        if (itemHash == infoHash.lowercase()) {
                            return item.optString("id", "")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
