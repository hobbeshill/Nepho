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
            "WHERE isDismissed = 0 AND (isStarred = 1 OR postTime >= :cutoffTime) " +
            "ORDER BY postTime DESC"
    )
    fun observeActiveNotifications(cutoffTime: Long): Flow<List<PulseNotificationEntity>>

    @Query(
        "SELECT * FROM extracted_links " +
            "WHERE isStarred = 1 " +
            "OR parentNotificationKey IN (SELECT `key` FROM pulse_notifications WHERE isStarred = 1 AND isDismissed = 0) " +
            "OR postTime >= :cutoffTime " +
            "ORDER BY postTime DESC"
    )
    fun observeActiveLinks(cutoffTime: Long): Flow<List<ExtractedLinkEntity>>

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

    @Query("UPDATE pulse_notifications SET isRead = 1 WHERE `key` = :key")
    suspend fun markAsRead(key: String)

    @Query("UPDATE pulse_notifications SET isDismissed = 1, reminderAt = NULL, reminderNote = NULL WHERE `key` = :key")
    suspend fun dismissByKey(key: String)

    @Query("UPDATE pulse_notifications SET isStarred = :starred WHERE `key` = :key")
    suspend fun updateNotificationStar(key: String, starred: Boolean)

    @Query("UPDATE extracted_links SET isStarred = :starred WHERE parentNotificationKey = :parentKey")
    suspend fun updateChildLinksStar(parentKey: String, starred: Boolean)

    @Transaction
    suspend fun setNotificationStarred(key: String, starred: Boolean) {
        updateNotificationStar(key, starred)
        updateChildLinksStar(key, starred)
    }

    @Query("UPDATE extracted_links SET isStarred = :starred WHERE id = :linkId")
    suspend fun setLinkStarred(linkId: String, starred: Boolean)

    @Query(
        "UPDATE pulse_notifications " +
            "SET reminderAt = :reminderAt, reminderNote = :reminderNote " +
            "WHERE `key` = :key"
    )
    suspend fun updateReminder(key: String, reminderAt: Long?, reminderNote: String?)

    @Query(
        "UPDATE pulse_notifications " +
            "SET repliesJson = :repliesJson, isRead = 1 " +
            "WHERE `key` = :key"
    )
    suspend fun updateReplies(key: String, repliesJson: String)

    @Query("DELETE FROM pulse_notifications WHERE isStarred = 0 AND postTime < :cutoffTime")
    suspend fun deleteExpiredUnstarredNotifications(cutoffTime: Long): Int

    @Query(
        "DELETE FROM extracted_links " +
            "WHERE isStarred = 0 " +
            "AND parentNotificationKey NOT IN (SELECT `key` FROM pulse_notifications WHERE isStarred = 1) " +
            "AND postTime < :cutoffTime"
    )
    suspend fun deleteExpiredUnstarredLinks(cutoffTime: Long): Int

    @Transaction
    suspend fun purgeExpiredBefore(cutoffTime: Long): Int {
        val deletedNotifications = deleteExpiredUnstarredNotifications(cutoffTime)
        val deletedLinks = deleteExpiredUnstarredLinks(cutoffTime)
        return deletedNotifications + deletedLinks
    }

    @Transaction
    suspend fun clearUnstarredSessionData() {
        deleteUnstarredLinks()
        deleteUnstarredNotifications()
    }

    @Query(
        "DELETE FROM extracted_links " +
            "WHERE isStarred = 0 " +
            "AND parentNotificationKey NOT IN (SELECT `key` FROM pulse_notifications WHERE isStarred = 1)"
    )
    suspend fun deleteUnstarredLinks()

    @Query("DELETE FROM pulse_notifications WHERE isStarred = 0")
    suspend fun deleteUnstarredNotifications()

    @Query("SELECT imageUri FROM pulse_notifications WHERE imageUri IS NOT NULL")
    suspend fun getAllImageUris(): List<String>
}
