package com.example.data

import android.net.Uri
import java.security.MessageDigest

data class ParsedUrl(
    val url: String,
    val normalizedUrl: String,
    val domain: String
)

object UrlExtractor {
    private val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '\'')

    fun extractUrls(vararg texts: String): List<ParsedUrl> {
        val seen = LinkedHashMap<String, ParsedUrl>()
        for (text in texts) {
            if (text.isBlank()) continue
            urlRegex.findAll(text).forEach { match ->
                val cleaned = cleanTrailingPunctuation(match.value)
                if (cleaned.length > 8) {
                    val parsed = parseUrl(cleaned)
                    if (parsed != null && !seen.containsKey(parsed.normalizedUrl)) {
                        seen[parsed.normalizedUrl] = parsed
                    }
                }
            }
        }
        return seen.values.toList()
    }

    private fun cleanTrailingPunctuation(raw: String): String {
        var current = raw
        while (current.isNotEmpty() && current.last() in trailingPunctuation) {
            if (current.last() == ')' && current.count { it == '(' } >= current.count { it == ')' }) {
                break
            }
            current = current.dropLast(1)
        }
        return current
    }

    private fun parseUrl(url: String): ParsedUrl? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase()?.removePrefix("www.")
                ?: return null
            val normalized = url.trimEnd('/')
            ParsedUrl(
                url = url,
                normalizedUrl = normalized,
                domain = host
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deterministicId(normalizedUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedUrl.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
