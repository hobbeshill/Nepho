package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseDao {
    @Query(
        "SELECT * FROM pulse_notifications " +
            "WHERE isDismissed = 0 AND postTime >= :cutoffTime " +
            "ORDER BY postTime DESC"
    )
    fun observeActiveNotifications(cutoffTime: Long): Flow<List<PulseNotificationEntity>>

    @Query(
        "SELECT * FROM pulse_notifications " +
            "WHERE isDismissed = 0 AND postTime >= :cutoffTime " +
            "ORDER BY postTime DESC"
    )
    suspend fun getActiveNotificationsSnapshot(cutoffTime: Long): List<PulseNotificationEntity>

    @Query(
        "SELECT * FROM extracted_links " +
            "WHERE postTime >= :cutoffTime " +
            "ORDER BY postTime DESC"
    )
    fun observeActiveLinks(cutoffTime: Long): Flow<List<ExtractedLinkEntity>>

    @Query(
        "SELECT * FROM digest_summaries " +
            "WHERE id = 1 AND generatedAt >= :cutoffTime LIMIT 1"
    )
    fun observeLatestDigest(cutoffTime: Long): Flow<DigestSummaryEntity?>

    @Query("SELECT * FROM pulse_notifications WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): PulseNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(entity: PulseNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<ExtractedLinkEntity>)

    @Transaction
    suspend fun insertNotificationWithLinks(
        notification: PulseNotificationEntity,
        links: List<ExtractedLinkEntity>
    ) {
        insertNotification(notification)
        if (links.isNotEmpty()) {
            insertLinks(links)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDigest(digest: DigestSummaryEntity)

    @Query("UPDATE pulse_notifications SET isRead = 1 WHERE `key` = :key")
    suspend fun markAsRead(key: String)

    @Query("UPDATE pulse_notifications SET isDismissed = 1 WHERE `key` = :key")
    suspend fun dismissByKey(key: String)

    @Query(
        "UPDATE pulse_notifications " +
            "SET repliesJson = :repliesJson, isRead = 1 " +
            "WHERE `key` = :key"
    )
    suspend fun updateReplies(key: String, repliesJson: String)

    @Query("DELETE FROM pulse_notifications WHERE postTime < :cutoffTime")
    suspend fun deleteNotificationsOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM extracted_links WHERE postTime < :cutoffTime")
    suspend fun deleteLinksOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM digest_summaries WHERE generatedAt < :cutoffTime")
    suspend fun deleteDigestsOlderThan(cutoffTime: Long): Int

    @Transaction
    suspend fun purgeExpiredBefore(cutoffTime: Long): Int {
        val deletedNotifications = deleteNotificationsOlderThan(cutoffTime)
        val deletedLinks = deleteLinksOlderThan(cutoffTime)
        deleteDigestsOlderThan(cutoffTime)
        return deletedNotifications + deletedLinks
    }

    @Transaction
    suspend fun clearAll() {
        deleteAllLinks()
        deleteAllNotifications()
        deleteAllDigests()
    }

    @Query("DELETE FROM extracted_links")
    suspend fun deleteAllLinks()

    @Query("DELETE FROM pulse_notifications")
    suspend fun deleteAllNotifications()

    @Query("DELETE FROM digest_summaries")
    suspend fun deleteAllDigests()
}
