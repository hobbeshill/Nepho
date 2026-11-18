package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pulse_notifications",
    indices = [
        Index(value = ["postTime"]),
        Index(value = ["packageName"])
    ]
)
data class PulseNotificationEntity(
    @PrimaryKey
    val key: String,
    val notificationId: Int,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val body: String,
    val postTime: Long,
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
    val canReply: Boolean = false,
    val secondaryActionsJson: String = "[]",
    val repliesJson: String = "[]",
    val urlsJson: String = "[]",
    val isSimulated: Boolean = false,
    val sourceChannel: String = "SHARE_SHEET"
)

@Entity(
    tableName = "extracted_links",
    foreignKeys = [
        ForeignKey(
            entity = PulseNotificationEntity::class,
            parentColumns = ["key"],
            childColumns = ["parentNotificationKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parentNotificationKey"]),
        Index(value = ["postTime"])
    ]
)
data class ExtractedLinkEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val normalizedUrl: String,
    val domain: String,
    val parentNotificationKey: String,
    val packageName: String,
    val appLabel: String,
    val senderTitle: String,
    val postTime: Long
)

@Entity(tableName = "digest_summaries")
data class DigestSummaryEntity(
    @PrimaryKey
    val id: Int = 1,
    val generatedAt: Long,
    val mode: String,
    val headline: String,
    val appGroupsJson: String,
    val actionItemsJson: String,
    val messageCount: Int
)
