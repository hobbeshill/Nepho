package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.GmailRed
import com.example.ui.theme.GroupMeCyan
import com.example.ui.theme.MessagesEmerald
import com.example.ui.theme.OtherSlate
import com.example.ui.theme.OutlookBlue
import com.example.ui.theme.TeamSnapOrange
import com.example.ui.theme.TwitterSky

enum class SupportedApp(
    val packageName: String,
    val displayName: String,
    val shortBadge: String,
    val accentColor: Color
) {
    OUTLOOK(
        packageName = "com.microsoft.office.outlook",
        displayName = "Outlook",
        shortBadge = "OL",
        accentColor = OutlookBlue
    ),
    GMAIL(
        packageName = "com.google.android.gm",
        displayName = "Gmail",
        shortBadge = "GM",
        accentColor = GmailRed
    ),
    GROUPME(
        packageName = "com.groupme.android",
        displayName = "GroupMe",
        shortBadge = "GR",
        accentColor = GroupMeCyan
    ),
    TEAMSNAP(
        packageName = "com.teamsnap.teamsnap",
        displayName = "TeamSnap",
        shortBadge = "TS",
        accentColor = TeamSnapOrange
    ),
    MESSAGES(
        packageName = "com.google.android.apps.messaging",
        displayName = "Messages",
        shortBadge = "SM",
        accentColor = MessagesEmerald
    ),
    TWITTER(
        packageName = "com.twitter.android",
        displayName = "X (Twitter)",
        shortBadge = "X",
        accentColor = TwitterSky
    ),
    OTHER(
        packageName = "com.example.shared",
        displayName = "Shared",
        shortBadge = "SH",
        accentColor = OtherSlate
    );

    companion object {
        val filterableApps = listOf(OUTLOOK, GMAIL, GROUPME, TEAMSNAP, MESSAGES, TWITTER)

        fun fromPackageName(packageName: String): SupportedApp =
            entries.firstOrNull { it.packageName == packageName } ?: OTHER

        fun detectFromSharedContent(subject: String?, text: String): SupportedApp {
            val combined = "${subject.orEmpty()} $text".lowercase()
            return when {
                "teamsnap" in combined || "practice" in combined || "roster" in combined -> TEAMSNAP
                "groupme" in combined -> GROUPME
                "outlook" in combined || "office365" in combined || "teams.microsoft" in combined -> OUTLOOK
                "gmail" in combined || "mail.google" in combined -> GMAIL
                "twitter" in combined || "x.com" in combined || "tweet" in combined -> TWITTER
                "sms:" in combined || "text message" in combined -> MESSAGES
                else -> OTHER
            }
        }
    }
}
