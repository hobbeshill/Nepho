package com.example.notifications

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import java.util.Locale

internal data class CommunicationFilterDecision(
    val isCommunication: Boolean,
    val isSilentOngoing: Boolean = false,
    val reason: String? = null
)

/**
 * Ensures Nepho's Feed only captures and displays real human/communication messages,
 * filtering out OS/system alerts, transient dismiss/undo confirmations (such as Gmail's
 * "Archived" / "Deleted" / "Undo" toasts), sync/backup/progress banners, and non-communication
 * app notifications.
 */
object CommunicationMessageFilter {

    private val COMMUNICATION_CATEGORIES: Set<String> = buildSet {
        add(Notification.CATEGORY_MESSAGE)
        add(Notification.CATEGORY_EMAIL)
        add(Notification.CATEGORY_SOCIAL)
        add(Notification.CATEGORY_CALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Notification.CATEGORY_MISSED_CALL)
        } else {
            add("missed_call")
        }
    }

    private val NON_COMMUNICATION_CATEGORIES: Set<String> = buildSet {
        add(Notification.CATEGORY_SYSTEM)
        add(Notification.CATEGORY_SERVICE)
        add(Notification.CATEGORY_PROGRESS)
        add(Notification.CATEGORY_STATUS)
        add(Notification.CATEGORY_TRANSPORT)
        add(Notification.CATEGORY_ERROR)
        add(Notification.CATEGORY_RECOMMENDATION)
        add(Notification.CATEGORY_PROMO)
        add(Notification.CATEGORY_NAVIGATION)
        add(Notification.CATEGORY_ALARM)
        add(Notification.CATEGORY_EVENT)
        add(Notification.CATEGORY_REMINDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Notification.CATEGORY_STOPWATCH)
            add(Notification.CATEGORY_WORKOUT)
            add(Notification.CATEGORY_LOCATION_SHARING)
        } else {
            add("stopwatch")
            add("workout")
            add("location_sharing")
        }
    }

    private val SYSTEM_OR_NON_COMM_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.providers.downloads",
        "com.android.providers.downloads.ui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.bluetooth",
        "com.android.shell",
        "com.android.nfc",
        "com.android.printspooler",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.captiveportallogin",
        "com.android.setupwizard",
        "com.google.android.setupwizard",
        "com.google.android.apps.wellbeing",
        "com.google.android.as",
        "com.google.android.as.oss",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.apps.safetyhub",
        "com.google.android.apps.restore",
        "com.google.android.apps.nbu.files",
        "com.android.updater",
        "com.samsung.android.lool",
        "com.samsung.android.app.smartcapture",
        "com.sec.android.app.myfiles",
        "com.google.android.calendar",
        "com.android.calendar",
        "com.google.android.apps.maps",
        "com.google.android.googlequicksearchbox"
    )

    private val SYSTEM_PACKAGE_PREFIXES: List<String> = listOf(
        "com.android.networkstack",
        "com.android.wifi",
        "com.google.android.overlay.",
        "com.samsung.android.bixby"
    )

    private val KNOWN_COMMUNICATION_PACKAGES: Set<String> = buildSet {
        SupportedApp.filterableApps.forEach { add(it.packageName) }
        // SMS / RCS / Phone / Voicemail
        add("com.samsung.android.messaging")
        add("com.android.mms")
        add("com.textra")
        add("xyz.klinker.messenger")
        add("com.p1.chompsms")
        add("com.verizon.messaging.vzmsgs")
        add("com.google.android.apps.googlevoice")
        add("com.google.android.dialer")
        add("com.android.dialer")
        add("com.samsung.android.dialer")
        add("com.android.server.telecom")
        // Messaging & Team Chat
        add("com.whatsapp")
        add("com.whatsapp.w4b")
        add("org.thoughtcrime.securesms")
        add("org.telegram.messenger")
        add("org.telegram.messenger.web")
        add("com.facebook.orca")
        add("com.facebook.mlite")
        add("com.discord")
        add("com.Slack")
        add("com.microsoft.teams")
        add("com.google.android.apps.dynamite")
        add("com.viber.voip")
        add("jp.naver.line.android")
        add("com.tencent.mm")
        add("com.snapchat.android")
        add("com.skype.raider")
        add("us.zoom.videomeetings")
        add("com.google.android.apps.tachyon")
        add("com.webex.teams")
        add("im.vector.app")
        add("com.wire")
        add("ch.threema.app")
        // Email clients
        add("com.yahoo.mobile.client.android.mail")
        add("ch.protonmail.android")
        add("com.readdle.spark")
        add("me.bluemail.mail")
        add("eu.faircode.email")
        add("com.fsck.k9")
        add("net.thunderbird.android")
        add("com.fastmail.app")
        add("com.easilydo.mail")
        add("com.samsung.android.email.provider")
        add("com.aol.mobile.aolapp")
        add("com.zoho.mail")
        add("com.boxer.email")
        // Social & Group Communication
        add("com.instagram.android")
        add("com.instagram.barcelona")
        add("com.facebook.katana")
        add("com.linkedin.android")
        add("com.reddit.frontpage")
        add("xyz.blueskyweb.app")
        add("org.joinmastodon.android")
        add("com.nhn.android.band")
        add("com.remind101")
        add("com.classdojo.android")
        add("com.sportngin.android")
        add("io.gc.app")
        add("com.heja.app")
        add("com.nextdoor")
    }

    private val SYSTEM_CHANNEL_KEYWORDS: List<String> = listOf(
        "foreground",
        "background_service",
        "background_work",
        "background_failure",
        "connected_to_web",
        "web_login",
        "media_playback",
        "sending_media",
        "sending_message",
        "chat_history_backup",
        "silent_notifications",
        "critical_app_alerts",
        "app_update",
        "system_status",
        "system_alert",
        "download",
        "upload",
        "backup",
        "restore",
        "sync",
        "undo",
        "toast"
    )

    private val MISC_SYSTEM_CHANNEL_EXACT: Set<String> = setOf(
        "miscellaneous",
        "misc",
        "other_notifications",
        "bugle_misc_channel",
        "general_system"
    )

    // Matches Gmail / email / messaging transient action confirmations & undo toasts
    private val TRANSIENT_CONFIRMATION_REGEX = Regex(
        pattern = "^(?:" +
            "(?:\\d+\\s+)?(?:(?:message|messages|conversation|conversations|email|emails|mail|item|items|thread|threads|notification|notifications)\\s+)?" +
            "(?:archived|deleted|trashed|snoozed|muted|unmuted|starred|unstarred|dismissed|cleared|removed|restored|undone|" +
            "marked as read|marked as unread|marked as spam|marked as important|reported(?: as)? spam|reported(?: as)? phishing|" +
            "moved to trash|moved to spam|moved to inbox|moved to archive|moved to bin)" +
            "|(?:undo|tap to undo|touch to undo|action undone|undoing\\.?\\.?\\.?)" +
            "|(?:(?:message|email|mail|reply|draft)\\s+(?:sent|saved|discarded|deleted|queued|scheduled))" +
            "|(?:sending(?:\\s+(?:message|email|mail|reply|media|photo|video|file|attachment))?\\.{0,3})" +
            "|(?:sent|draft saved|queued for sending|waiting to send)" +
            ")\\.?$",
        option = RegexOption.IGNORE_CASE
    )

    // Matches app system/background/sync/connection/device status messages
    private val SYSTEM_STATUS_REGEX = Regex(
        pattern = "^(?:" +
            "(?:checking|syncing|synchronizing|updating|refreshing|loading|fetching|preparing|initializing|connecting|reconnecting|signing in|logging in|verifying)" +
            "(?:\\s+(?:for\\s+)?(?:new\\s+)?(?:mail|email|emails|messages|conversations|chats|inbox|account|data|contacts|app|updates|backup))?\\.{0,3}" +
            "|(?:backing up|restoring|uploading|downloading)\\s+(?:messages|chats|chat history|media|attachments?|files?|photos?|videos?|emails?|data|backup).*" +
            "|(?:backup|chat backup|download|upload|sync|update|installation)\\s+(?:complete|completed|finished|failed|paused|in progress|queued)\\.?" +
            "|.*(?:is currently active|connected to web|messages for web|whatsapp web|device pairing|linked device|running in the background|displaying over other apps|using battery|usb debugging|screenshot saved|screen recording|copied to clipboard).*" +
            "|(?:waiting for (?:network|connection|wi-?fi)|no internet connection|you'?re offline|offline mode|unable to sync|sync error|sign-?in required|account action required|storage almost full|low battery|battery saver).*" +
            ")$",
        option = RegexOption.IGNORE_CASE
    )

    private val EMAIL_ADDRESS_ONLY_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    internal fun evaluateIncoming(
        sbn: StatusBarNotification,
        extracted: ExtractedNotificationContent,
        resolvedTitle: String,
        resolvedBody: String,
        appLabel: String
    ): CommunicationFilterDecision {
        val notification = sbn.notification
            ?: return CommunicationFilterDecision(isCommunication = false, isSilentOngoing = true)
        val extras = notification.extras ?: Bundle.EMPTY

        // 1. Ongoing, foreground service, and non-clearable notifications (silent skip)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (notification.flags and Notification.FLAG_NO_CLEAR) != 0
        ) {
            return CommunicationFilterDecision(
                isCommunication = false,
                isSilentOngoing = true,
                reason = "Ongoing or persistent system service notification."
            )
        }

        // 2. Active progress bar notifications (downloads, uploads, syncing, backups)
        val hasProgress = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
        if (hasProgress) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped background progress notification."
            )
        }

        // 3. Explicit non-communication Android categories
        val category = notification.category
        if (category != null && category in NON_COMMUNICATION_CATEGORIES) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped non-communication notification (category: $category)."
            )
        }

        // 4. OS / System utility packages
        if (isSystemPackage(sbn.packageName)) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped Android system/utility notification."
            )
        }

        // Inspect notification structure & actions
        val actions = notification.actions ?: emptyArray()
        val hasRemoteInputReply = actions.any { !it.remoteInputs.isNullOrEmpty() && it.actionIntent != null }
        val actionLabels = actions.mapNotNull { it.title?.toString()?.trim()?.takeIf { t -> t.isNotEmpty() } }
        val hasMessagingStyle = hasMessagingStyle(extras)
        val hasInboxStyle = hasInboxStyle(extras)
        val hasPeopleMetadata = hasPeopleMetadata(extras)

        // 5. Post-action Undo confirmation toasts (e.g. Gmail "Archived" / "Deleted" [Undo])
        if (!hasRemoteInputReply && !hasMessagingStyle && hasUndoAction(actionLabels)) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped dismiss/undo confirmation toast notification."
            )
        }

        // 6. System / background / misc notification channels (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !hasRemoteInputReply && !hasMessagingStyle) {
            val channelId = notification.channelId?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (channelId.isNotEmpty() && isSystemChannel(channelId, hasInboxStyle)) {
                return CommunicationFilterDecision(
                    isCommunication = false,
                    reason = "Skipped system/background channel notification ($channelId)."
                )
            }
        }

        // 7. Transient confirmation toast / app system status wording
        if (!hasRemoteInputReply && !hasMessagingStyle &&
            isTransientOrSystemText(resolvedTitle, resolvedBody, appLabel)
        ) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped system status or action confirmation message."
            )
        }

        // 8. Require at least one positive communication signal (communication category,
        //    direct messaging structure, person metadata, or known communication app)
        val isExplicitCommunicationCategory = category != null && category in COMMUNICATION_CATEGORIES
        val isKnownCommApp = isKnownCommunicationPackage(sbn.packageName)
        val isCommunication = isExplicitCommunicationCategory ||
            hasMessagingStyle ||
            hasRemoteInputReply ||
            hasPeopleMetadata ||
            isKnownCommApp

        if (!isCommunication) {
            return CommunicationFilterDecision(
                isCommunication = false,
                reason = "Skipped non-communication app notification."
            )
        }

        return CommunicationFilterDecision(isCommunication = true)
    }

    /**
     * Display-layer guard so any previously captured Gmail dismiss toasts or system messages
     * already stored in Room are also excluded from the Feed, unread counts, and home widget.
     */
    fun shouldDisplayInFeed(item: PulseNotificationItem): Boolean {
        // Always keep explicit user-created or share-sheet items
        if (item.sourceChannel == "MANUAL" || item.sourceChannel == "SHARE_SHEET") {
            return true
        }
        if (isSystemPackage(item.packageName)) {
            return false
        }
        if (!item.canReply && hasUndoAction(item.secondaryActions)) {
            return false
        }
        if (!item.canReply && isTransientOrSystemText(item.title, item.body, item.appLabel)) {
            return false
        }
        return true
    }

    internal fun isSystemPackage(packageName: String): Boolean {
        val pkg = packageName.trim().lowercase(Locale.ROOT)
        if (pkg in SYSTEM_OR_NON_COMM_PACKAGES) return true
        return SYSTEM_PACKAGE_PREFIXES.any { pkg.startsWith(it) }
    }

    internal fun isKnownCommunicationPackage(packageName: String): Boolean {
        return packageName in KNOWN_COMMUNICATION_PACKAGES ||
            SupportedApp.fromPackageName(packageName) != SupportedApp.OTHER
    }

    private fun hasUndoAction(actionLabels: List<String>): Boolean {
        return actionLabels.any { raw ->
            val normalized = raw.trim().lowercase(Locale.ROOT)
            normalized == "undo" || normalized.startsWith("undo ")
        }
    }

    private fun isSystemChannel(channelIdLower: String, hasInboxStyle: Boolean): Boolean {
        if (!hasInboxStyle && channelIdLower in MISC_SYSTEM_CHANNEL_EXACT) {
            return true
        }
        val tokens = channelIdLower.split('_', '-', '.', '/', ':', ' ').filter { it.isNotEmpty() }
        return SYSTEM_CHANNEL_KEYWORDS.any { keyword ->
            if ('_' in keyword) {
                channelIdLower.contains(keyword)
            } else {
                keyword in tokens
            }
        }
    }

    internal fun isTransientOrSystemText(title: String, body: String, appLabel: String): Boolean {
        val cleanTitle = normalizeWhitespace(title)
        val cleanBody = normalizeWhitespace(body)
        val cleanApp = normalizeWhitespace(appLabel)

        val titleIsConfirm = cleanTitle.isNotEmpty() && TRANSIENT_CONFIRMATION_REGEX.matches(cleanTitle)
        val bodyIsConfirm = cleanBody.isNotEmpty() && TRANSIENT_CONFIRMATION_REGEX.matches(cleanBody)
        val titleIsStatus = cleanTitle.isNotEmpty() && SYSTEM_STATUS_REGEX.matches(cleanTitle)
        val bodyIsStatus = cleanBody.isNotEmpty() && SYSTEM_STATUS_REGEX.matches(cleanBody)

        // Body itself is clearly a confirmation toast or system status message
        if (bodyIsConfirm || bodyIsStatus) return true

        // Title is a confirmation toast/status AND body is empty, duplicate, app label, or account email
        if (titleIsConfirm || titleIsStatus) {
            val bodyIsBoilerplate = cleanBody.isEmpty() ||
                cleanBody.equals(cleanTitle, ignoreCase = true) ||
                cleanBody.equals(cleanApp, ignoreCase = true) ||
                EMAIL_ADDRESS_ONLY_REGEX.matches(cleanBody)
            if (bodyIsBoilerplate) return true
        }

        return false
    }

    @Suppress("DEPRECATION")
    private fun hasMessagingStyle(extras: Bundle): Boolean {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!messages.isNullOrEmpty()) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.endsWith("MessagingStyle")
    }

    private fun hasInboxStyle(extras: Bundle): Boolean {
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.endsWith("InboxStyle")
    }

    @Suppress("DEPRECATION")
    private fun hasPeopleMetadata(extras: Bundle): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val peopleList = runCatching {
                extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
            }.getOrNull()
            if (!peopleList.isNullOrEmpty()) return true
        }
        val legacyPeople = runCatching {
            extras.getStringArray(Notification.EXTRA_PEOPLE)
        }.getOrNull()
        return !legacyPeople.isNullOrEmpty()
    }

    private fun normalizeWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
