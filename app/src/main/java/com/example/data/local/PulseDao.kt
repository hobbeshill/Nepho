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
        "SELECT * FROM pulse_notifications " +
            "WHERE isDismissed = 0 AND (isStarred = 1 OR postTime >= :cutoffTime) " +
            "ORDER BY postTime DESC"
    )
    suspend fun getActiveNotificationsSnapshot(cutoffTime: Long): List<PulseNotificationEntity>

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
        links: List<ExtractedLinkEntity>,
        digestEvent: DigestEventEntity
    ) {
        insertNotification(notification)
        if (links.isNotEmpty()) {
            insertLinks(links)
        }
        if (getDigestSettings()?.enabled == true) {
            insertDigestEvent(digestEvent.copy(capturedAt = System.currentTimeMillis()))
        }
    }

    @Query("SELECT * FROM digest_settings WHERE id = 1")
    suspend fun getDigestSettings(): DigestSettingsEntity?

    @Query("SELECT * FROM digest_settings WHERE id = 1")
    fun observeDigestSettings(): Flow<DigestSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDigestSettings(settings: DigestSettingsEntity)

    @Transaction
    suspend fun setDigestEnabled(enabled: Boolean, initialEnd: Long): DigestSettingsEntity {
        val settings = getDigestSettings() ?: DigestSettingsEntity(enabled = false, nextEnd = initialEnd)
        return settings.copy(enabled = enabled, lastError = null).also { putDigestSettings(it) }
    }

    @Query("UPDATE digest_settings SET lastError = :message WHERE id = 1")
    suspend fun setDigestError(message: String?)

    @Insert
    suspend fun insertDigestEvent(event: DigestEventEntity)

    @Query("SELECT * FROM digest_events WHERE capturedAt < :endTime ORDER BY capturedAt, id")
    suspend fun getDigestEventsBefore(endTime: Long): List<DigestEventEntity>

    @Query("DELETE FROM digest_events WHERE capturedAt < :endTime")
    suspend fun deleteDigestEventsBefore(endTime: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDigestSummary(summary: DigestSummaryEntity)

    @Query("SELECT * FROM digest_summaries ORDER BY endTime DESC")
    fun observeDigestSummaries(): Flow<List<DigestSummaryEntity>>

    @Transaction
    suspend fun completeDigest(summary: DigestSummaryEntity, nextEnd: Long): Boolean {
        val settings = getDigestSettings() ?: return false
        if (!settings.enabled || settings.nextEnd != summary.endTime) return false
        insertDigestSummary(summary)
        deleteDigestEventsBefore(summary.endTime)
        putDigestSettings(settings.copy(nextEnd = nextEnd, lastError = null))
        return true
    }

    @Query("UPDATE pulse_notifications SET isRead = 1 WHERE `key` = :key")
    suspend fun markAsRead(key: String)

    @Query("UPDATE pulse_notifications SET isRead = 1 WHERE packageName = :packageName AND isDismissed = 0 AND isRead = 0")
    suspend fun markPackageAsRead(packageName: String): Int

    @Query("UPDATE pulse_notifications SET isDismissed = 1, reminderAt = NULL, reminderNote = NULL WHERE `key` = :key")
    suspend fun dismissByKey(key: String)

    @Query(
        "UPDATE pulse_notifications " +
            "SET isDismissed = 0, reminderAt = :reminderAt, reminderNote = :reminderNote " +
            "WHERE `key` = :key"
    )
    suspend fun restoreDismissedByKey(key: String, reminderAt: Long?, reminderNote: String?)

    @Query(
        "SELECT * FROM pulse_notifications " +
            "WHERE packageName = :packageName AND isDismissed = 0 AND isStarred = 0 AND postTime >= :cutoffTime"
    )
    suspend fun getActiveUnstarredByPackage(packageName: String, cutoffTime: Long): List<PulseNotificationEntity>

    @Query(
        "SELECT COUNT(*) FROM pulse_notifications " +
            "WHERE packageName = :packageName AND isDismissed = 0 AND isStarred = 1"
    )
    suspend fun countActiveStarredByPackage(packageName: String): Int

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

    @Query("SELECT * FROM capture_diagnostics ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeCaptureDiagnostics(limit: Int = 100): Flow<List<CaptureDiagnosticEntity>>

    @Insert
    suspend fun insertCaptureDiagnostic(entry: CaptureDiagnosticEntity)

    @Query(
        "DELETE FROM capture_diagnostics " +
            "WHERE id NOT IN (SELECT id FROM capture_diagnostics ORDER BY timestamp DESC, id DESC LIMIT :maxRows)"
    )
    suspend fun trimCaptureDiagnostics(maxRows: Int = 100)

    @Transaction
    suspend fun recordCaptureDiagnostic(entry: CaptureDiagnosticEntity, maxRows: Int = 100) {
        insertCaptureDiagnostic(entry)
        trimCaptureDiagnostics(maxRows)
    }

    @Query("DELETE FROM capture_diagnostics")
    suspend fun clearCaptureDiagnostics()
}

