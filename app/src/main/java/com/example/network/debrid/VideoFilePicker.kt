package com.example.network.debrid

import java.util.regex.Pattern

data class DebridFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long
)

object VideoFilePicker {
    private val VIDEO_EXTENSIONS = setOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".m4v", ".ts", ".m2ts")
    private val IGNORED_KEYWORDS = listOf(
        "sample", "trailer", "extras", "featurette", "behindthescenes", "promo",
        "18+游戏", "996gg", "qq群", "telegram", "tg群", "广告", "澳门", "赌场"
    )

    // Regex for JAV codes (e.g. SNOS-308, IPX-123, SSIS001)
    private val JAV_CODE_PATTERN = Pattern.compile("([a-zA-Z]{2,6})[-_]?(\\d{3,5})", Pattern.CASE_INSENSITIVE)

    /**
     * Minimum file size threshold for main video content (100 MB).
     * Smaller files (e.g. 1.91 MB promo ads) will be filtered out if larger main video files exist.
     */
    private const val MIN_MAIN_VIDEO_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB

    fun pickBestVideoFile(files: List<DebridFileInfo>, episodeHint: String? = null): DebridFileInfo? {
        if (files.isEmpty()) return null

        // 1. Filter valid video extensions and exclude known promo keywords
        val videoFiles = files.filter { file ->
            val lowerName = file.name.lowercase()
            val isVideo = VIDEO_EXTENSIONS.any { ext -> lowerName.endsWith(ext) }
            val isExplicitIgnored = IGNORED_KEYWORDS.any { kw -> lowerName.contains(kw) }
            isVideo && !isExplicitIgnored
        }

        if (videoFiles.isEmpty()) return null

        val maxSizeBytes = videoFiles.maxOf { it.sizeBytes }

        // 2. Relative Size Ratio & Threshold Filter:
        // Exclude promo ads/short clips whose size is < 5% of the largest file in the torrent,
        // OR < 100MB if the largest file is > 500MB.
        val mainCandidates = videoFiles.filter { file ->
            if (maxSizeBytes > 500 * 1024 * 1024L) {
                file.sizeBytes >= MIN_MAIN_VIDEO_SIZE_BYTES && (file.sizeBytes.toDouble() / maxSizeBytes) >= 0.05
            } else {
                true
            }
        }.ifEmpty { videoFiles }

        // 3. JAV Code & Search Hint Matching
        if (!episodeHint.isNullOrBlank()) {
            val hintLower = episodeHint.lowercase().trim()
            val javMatcher = JAV_CODE_PATTERN.matcher(hintLower)
            val extractedJavCode = if (javMatcher.find()) {
                val prefix = javMatcher.group(1)?.lowercase() ?: ""
                val num = javMatcher.group(2) ?: ""
                prefix to num
            } else null

            // Match exact JAV code format in filename (e.g. "SNOS-308" or "SNOS_308" or "SNOS308")
            if (extractedJavCode != null) {
                val (prefix, num) = extractedJavCode
                val javMatchCandidate = mainCandidates.find { candidate ->
                    val cName = candidate.name.lowercase()
                    cName.contains("$prefix-$num") || cName.contains("${prefix}_$num") || cName.contains("$prefix$num")
                }
                if (javMatchCandidate != null) {
                    return javMatchCandidate
                }
            }

            // General hint match
            val stringMatchCandidate = mainCandidates.find { candidate ->
                candidate.name.lowercase().contains(hintLower)
            }
            if (stringMatchCandidate != null) {
                return stringMatchCandidate
            }
        }

        // 4. Default: Return the largest main video file candidate
        return mainCandidates.maxByOrNull { it.sizeBytes }
    }
}
