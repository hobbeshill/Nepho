package com.example.ai

import com.example.data.DigestActionItem
import com.example.data.DigestAppGroup
import com.example.data.DigestMode
import com.example.data.DigestResult
import com.example.data.PulseNotificationItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartDigestService {

    private val digestSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(
                description = "A concise one-sentence executive summary of the messages in the 24-hour window."
            ),
            "appGroups" to Schema.array(
                description = "Messages grouped by source app with 1-3 concise bullet points per app.",
                items = Schema.obj(
                    properties = mapOf(
                        "appName" to Schema.string(description = "Source application name (e.g. Outlook, Gmail, GroupMe, TeamSnap, Messages)"),
                        "bulletPoints" to Schema.array(
                            items = Schema.string(),
                            description = "Concise bullet summary points for this app"
                        )
                    )
                )
            ),
            "actionItems" to Schema.array(
                description = "Actionable follow-ups extracted from the messages (schedules, invoices, practices, games, urgent replies).",
                items = Schema.obj(
                    properties = mapOf(
                        "priority" to Schema.enumeration(
                            values = listOf("Urgent", "Schedule", "Billing", "Reply"),
                            description = "Category or urgency of the action item"
                        ),
                        "appName" to Schema.string(description = "Source app name"),
                        "sender" to Schema.string(description = "Sender or subject title"),
                        "summary" to Schema.string(description = "Clear actionable step required")
                    )
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = digestSchema
            }
        )
    }

    suspend fun generateAiDigest(messages: List<PulseNotificationItem>): DigestResult {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val transcript = buildString {
            appendLine("Summarize the following ${messages.size} notifications received within the active <24h window.")
            appendLine("Group bullet points cleanly by app, and extract concrete action items (especially anything about schedules, practices, games, invoices, urgent deadlines, or requested replies).")
            appendLine()
            messages.forEachIndexed { index, item ->
                val timeStr = timeFmt.format(Date(item.postTime))
                appendLine("[${index + 1}] App: ${item.appLabel} | Time: $timeStr | Read: ${item.isRead}")
                appendLine("Title: ${item.title}")
                appendLine("Message: ${item.body}")
                if (item.replies.isNotEmpty()) {
                    appendLine("User already replied: ${item.replies.joinToString(" ; ") { it.text }}")
                }
                appendLine("---")
            }
        }

        val response = withTimeout(60_000L) {
            model.generateContent(transcript)
        }
        val rawJson = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
        val json = JSONObject(rawJson)

        val headline = json.optString("headline", "Daily digest across ${messages.size} active alerts.")
        val groupsArray = json.optJSONArray("appGroups")
        val appGroups = mutableListOf<DigestAppGroup>()
        if (groupsArray != null) {
            for (i in 0 until groupsArray.length()) {
                val obj = groupsArray.getJSONObject(i)
                val bulletsArr = obj.optJSONArray("bulletPoints")
                val bullets = mutableListOf<String>()
                if (bulletsArr != null) {
                    for (j in 0 until bulletsArr.length()) {
                        bullets.add(bulletsArr.getString(j))
                    }
                }
                appGroups.add(
                    DigestAppGroup(
                        appName = obj.optString("appName", "Other"),
                        bulletPoints = bullets
                    )
                )
            }
        }

        val actionsArray = json.optJSONArray("actionItems")
        val actionItems = mutableListOf<DigestActionItem>()
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val obj = actionsArray.getJSONObject(i)
                actionItems.add(
                    DigestActionItem(
                        priority = obj.optString("priority", "Reply"),
                        appName = obj.optString("appName", "App"),
                        sender = obj.optString("sender", ""),
                        summary = obj.optString("summary", "")
                    )
                )
            }
        }

        return DigestResult(
            generatedAt = System.currentTimeMillis(),
            mode = DigestMode.GEMINI_AI,
            headline = headline,
            appGroups = appGroups,
            actionItems = actionItems,
            messageCount = messages.size
        )
    }

    fun generateLocalRuleBasedDigest(messages: List<PulseNotificationItem>): DigestResult {
        val grouped = messages.groupBy { it.appLabel }
        val appGroups = grouped.map { (appName, appItems) ->
            val bullets = appItems.map { item ->
                val preview = item.body.replace("\n", " ").take(110).let {
                    if (item.body.length > 110) "$it…" else it
                }
                "${item.title}: $preview"
            }
            DigestAppGroup(appName = appName, bulletPoints = bullets)
        }

        val actionKeywords = listOf(
            "urgent" to "Urgent",
            "action required" to "Urgent",
            "invoice" to "Billing",
            "receipt" to "Billing",
            "schedule" to "Schedule",
            "practice" to "Schedule",
            "game" to "Schedule",
            "reply" to "Reply",
            "confirm" to "Reply",
            "share" to "Reply"
        )

        val actionItems = mutableListOf<DigestActionItem>()
        messages.forEach { item ->
            val lower = "${item.title} ${item.body}".lowercase()
            val matched = actionKeywords.firstOrNull { (kw, _) -> kw in lower }
            if (matched != null) {
                val snippet = item.body.split(".", "\n").firstOrNull { it.isNotBlank() }?.trim()
                    ?: item.body.take(90)
                actionItems.add(
                    DigestActionItem(
                        priority = matched.second,
                        appName = item.appLabel,
                        sender = item.title,
                        summary = snippet
                    )
                )
            }
        }

        val unreadCount = messages.count { !it.isRead }
        val headline = "Local summary across ${messages.size} alerts ($unreadCount unread) from ${grouped.keys.size} apps in the 24h window."

        return DigestResult(
            generatedAt = System.currentTimeMillis(),
            mode = DigestMode.LOCAL_FALLBACK,
            headline = headline,
            appGroups = appGroups,
            actionItems = actionItems,
            messageCount = messages.size
        )
    }
}
