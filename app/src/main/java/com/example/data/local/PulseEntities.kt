package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pulse_notifications",
    indices = [
        Index(value = ["postTime"]),
        Index(value = ["packageName"]),
        Index(value = ["isStarred"])
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
    val imageUri: String? = null,
    val sourceChannel: String = "SHARE_SHEET",
    val isStarred: Boolean = false,
    val reminderAt: Long? = null,
    val reminderNote: String? = null
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
        Index(value = ["postTime"]),
        Index(value = ["isStarred"])
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
    val postTime: Long,
    val isStarred: Boolean = false
)

@Entity(tableName = "digest_settings")
data class DigestSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean,
    val nextEnd: Long,
    val lastError: String? = null
)

@Entity(tableName = "digest_events", indices = [Index(value = ["capturedAt"])])
data class DigestEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val appLabel: String,
    val title: String,
    val body: String,
    val urlsJson: String
)

@Entity(tableName = "digest_summaries")
data class DigestSummaryEntity(
    @PrimaryKey val endTime: Long,
    val startTime: Long,
    val summary: String,
    val urlsJson: String
)
