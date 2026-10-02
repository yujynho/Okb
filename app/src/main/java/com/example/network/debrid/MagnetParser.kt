package com.example.network.debrid

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

object MagnetParser {

    private val BTIH_HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{40}$")
    private val BTIH_BASE32_PATTERN = Pattern.compile("^[a-zA-Z2-7]{32}$")
    private val EXTRACT_PATTERN = Pattern.compile(
        "(?:xt=urn(?:%3A|:)btih(?:%3A|:))([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts and normalizes a 40-character lowercase hexadecimal info-hash from
     * a magnet URI, bare 40-hex hash, 32-char Base32 hash, URL-encoded string, or raw text.
     * Returns null if no valid hash can be parsed.
     */
    fun extractInfoHash(input: String?): String? {
        if (input.isNullOrBlank()) return null

        var cleaned = input.trim()
        // Replace HTML entity &amp; with standard &
        cleaned = cleaned.replace("&amp;", "&")

        // 1. Direct 40-character hex info-hash
        if (BTIH_HEX_PATTERN.matcher(cleaned).matches()) {
            return cleaned.lowercase(Locale.US)
        }

        // 2. Direct 32-character Base32 info-hash
        if (BTIH_BASE32_PATTERN.matcher(cleaned).matches()) {
            return base32ToHex(cleaned)?.lowercase(Locale.US)
        }

        // 3. Search via regex in raw string (handles xt=urn:btih:... and xt=urn%3Abtih%3A...)
        val matcher = EXTRACT_PATTERN.matcher(cleaned)
        if (matcher.find()) {
            val rawGroup = matcher.group(1) ?: return null
            return if (rawGroup.length == 40) {
                rawGroup.lowercase(Locale.US)
            } else if (rawGroup.length == 32) {
                base32ToHex(rawGroup)?.lowercase(Locale.US)
            } else {
                null
            }
        }

        // 4. Try URL decoding if percent encoding is present
        if (cleaned.contains("%")) {
            try {
                val decoded = URLDecoder.decode(cleaned, StandardCharsets.UTF_8.name())
                if (decoded != cleaned) {
                    return extractInfoHash(decoded)
                }
            } catch (_: Exception) {
                // Ignore URL decoding errors and continue
            }
        }

        return null
    }

    /**
     * Checks if the input is a valid 40-hex info-hash, 32-char base32 info-hash, or valid magnet URI.
     */
    fun isInfoHash(input: String?): Boolean {
        return extractInfoHash(input) != null
    }

    /**
     * Generates a standard canonical magnet URI from an info-hash, optional display name, and optional trackers.
     */
    fun toCanonicalMagnet(
        infoHash: String,
        displayName: String? = null,
        trackers: List<String> = emptyList()
    ): String {
        val normalizedHash = extractInfoHash(infoHash) ?: infoHash.trim().lowercase(Locale.US)
        val sb = StringBuilder("magnet:?xt=urn:btih:").append(normalizedHash)
        if (!displayName.isNullOrBlank()) {
            try {
                val encodedName = java.net.URLEncoder.encode(displayName.trim(), StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                sb.append("&dn=").append(encodedName)
            } catch (_: Exception) {
                sb.append("&dn=").append(displayName.trim())
            }
        }
        trackers.forEach { tr ->
            if (tr.isNotBlank()) {
                try {
                    val encodedTr = java.net.URLEncoder.encode(tr.trim(), StandardCharsets.UTF_8.name())
                        .replace("+", "%20")
                    sb.append("&tr=").append(encodedTr)
                } catch (_: Exception) {
                    sb.append("&tr=").append(tr.trim())
                }
            }
        }
        return sb.toString()
    }

    /**
     * Converts a 32-character RFC 4648 Base32 string (160 bits) into a 40-character hexadecimal string.
     */
    fun base32ToHex(base32: String): String? {
        val clean = base32.trim().uppercase(Locale.US)
        if (clean.length != 32) return null

        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0L
        var bitsLeft = 0
        val bytes = ByteArray(20)
        var byteIndex = 0

        for (i in 0 until clean.length) {
            val charVal = base32Chars.indexOf(clean[i])
            if (charVal < 0) return null // Invalid Base32 character

            buffer = (buffer shl 5) or charVal.toLong()
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (byteIndex < bytes.size) {
                    bytes[byteIndex++] = ((buffer shr bitsLeft) and 0xFF).toByte()
                }
            }
        }

        if (byteIndex != 20) return null

        val hexChars = "0123456789abcdef"
        val hex = StringBuilder(40)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append(hexChars[v ushr 4])
            hex.append(hexChars[v and 0x0F])
        }
        return hex.toString()
    }
}
