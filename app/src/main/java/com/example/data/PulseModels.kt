package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class ReplyEntry(
    val text: String,
    val sentAt: Long
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
    val isSimulated: Boolean,
    val sourceChannel: String,
    val isStarred: Boolean = false,
    val reminderAt: Long? = null,
    val reminderNote: String? = null
) {
    val supportedApp: SupportedApp
        get() = SupportedApp.fromPackageName(packageName)
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

enum class DigestMode {
    GEMINI_AI,
    LOCAL_FALLBACK
}

data class DigestAppGroup(
    val appName: String,
    val bulletPoints: List<String>
)

data class DigestActionItem(
    val priority: String,
    val appName: String,
    val sender: String,
    val summary: String
)

data class DigestResult(
    val generatedAt: Long,
    val mode: DigestMode,
    val headline: String,
    val appGroups: List<DigestAppGroup>,
    val actionItems: List<DigestActionItem>,
    val messageCount: Int
)

data class SessionMetrics(
    val totalInWindow: Int = 0,
    val unreadTotal: Int = 0,
    val unreadByApp: Map<SupportedApp, Int> = emptyMap(),
    val totalLinks: Int = 0
)

enum class ShadeMode {
    SILENT,
    NEPHO_ALERT
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
                    sentAt = obj.getLong("sentAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeAppGroups(groups: List<DigestAppGroup>): String {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(
                JSONObject().apply {
                    put("appName", group.appName)
                    put("bulletPoints", JSONArray(group.bulletPoints))
                }
            )
        }
        return array.toString()
    }

    fun decodeAppGroups(raw: String): List<DigestAppGroup> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                val bulletsJson = obj.getJSONArray("bulletPoints")
                val bullets = (0 until bulletsJson.length()).map { bulletsJson.getString(it) }
                DigestAppGroup(
                    appName = obj.getString("appName"),
                    bulletPoints = bullets
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeActionItems(items: List<DigestActionItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("priority", item.priority)
                    put("appName", item.appName)
                    put("sender", item.sender)
                    put("summary", item.summary)
                }
            )
        }
        return array.toString()
    }

    fun decodeActionItems(raw: String): List<DigestActionItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                DigestActionItem(
                    priority = obj.optString("priority", "Normal"),
                    appName = obj.optString("appName", "App"),
                    sender = obj.optString("sender", ""),
                    summary = obj.optString("summary", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
