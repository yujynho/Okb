package com.example.network.torrent

import java.util.regex.Pattern

object MagnetUtils {
    private val BTIH_PATTERN = Pattern.compile("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", Pattern.CASE_INSENSITIVE)

    /**
     * Extracts the 40-hex or 32-base32 BTIH infohash from a magnet link or raw text.
     */
    fun extractMagnetHash(magnet: String?): String? {
        if (magnet.isNullOrBlank()) return null
        val matcher = BTIH_PATTERN.matcher(magnet.trim())
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    /**
     * Validates whether a magnet URI contains a valid BTIH hash.
     */
    fun isValidMagnetUri(magnet: String?): Boolean {
        return extractMagnetHash(magnet) != null
    }
}
