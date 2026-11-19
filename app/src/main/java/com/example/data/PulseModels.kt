package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class ReplyEntry(
    val text: String,
    val sentAt: Long,
    val gifPath: String? = null
)

data class PulseNotificationItem(
    val key: String,
    val notificationId: Int,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val body: String,
    val postTime: Long,
    val isRead: Boolean,
    val isDismissed: Boolean,
    val canReply: Boolean,
    val secondaryActions: List<String>,
    val replies: List<ReplyEntry>,
    val urls: List<String>,
    val imageUri: String? = null,
    val sourceChannel: String,
    val isStarred: Boolean = false,
    val reminderAt: Long? = null,
    val reminderNote: String? = null
) {
    val supportedApp: SupportedApp
        get() = SupportedApp.fromPackageName(packageName)

    /** Conversation identity: all notifications from the same app with the same title form a thread. */
    val threadId: String
        get() = "$packageName|${title.trim().lowercase()}"

    /** URLs in the message body that point at directly renderable images or GIFs. */
    val imageUrls: List<String>
        get() = urls.filter { UrlExtractor.isImageUrl(it) }
}

data class ExtractedLinkItem(
    val id: String,
    val url: String,
    val normalizedUrl: String,
    val domain: String,
    val parentNotificationKey: String,
    val packageName: String,
    val appLabel: String,
    val senderTitle: String,
    val postTime: Long,
    val isStarred: Boolean = false
) {
    val supportedApp: SupportedApp
        get() = SupportedApp.fromPackageName(packageName)
}

data class SessionMetrics(
    val totalInWindow: Int = 0,
    val unreadTotal: Int = 0,
    val unreadByApp: Map<SupportedApp, Int> = emptyMap(),
    val totalLinks: Int = 0
)

enum class ShadeMode {
    /** Silenced: captured into the Nepho feed only; no alert anywhere. */
    SILENT,

    /** Allowed: captured and re-surfaced as a single Nepho shade alert. */
    NEPHO_ALERT,

    /** Not allowed: original notification is cancelled and nothing is captured. */
    BLOCKED
}

data class AppShadePreference(
    val packageName: String,
    val appLabel: String,
    val isOptedIn: Boolean,
    val shadeMode: ShadeMode,
    val isCoreApp: Boolean
)

object PulseJsonCodec {
    fun encodeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeReplies(replies: List<ReplyEntry>): String {
        val array = JSONArray()
        replies.forEach { reply ->
            array.put(
                JSONObject().apply {
                    put("text", reply.text)
                    put("sentAt", reply.sentAt)
                    if (!reply.gifPath.isNullOrBlank()) {
                        put("gifPath", reply.gifPath)
                    }
                }
            )
        }
        return array.toString()
    }

    fun decodeReplies(raw: String): List<ReplyEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                ReplyEntry(
                    text = obj.getString("text"),
                    sentAt = obj.getLong("sentAt"),
                    gifPath = obj.optString("gifPath").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
