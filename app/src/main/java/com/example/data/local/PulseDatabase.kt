package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PulseNotificationEntity::class,
        ExtractedLinkEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun pulseDao(): PulseDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pulse_notifications ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pulse_notifications ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE pulse_notifications ADD COLUMN reminderNote TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pulse_notifications_isStarred ON pulse_notifications(isStarred)")
                db.execSQL("ALTER TABLE extracted_links ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_extracted_links_isStarred ON extracted_links(isStarred)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The digest feature was removed entirely
                db.execSQL("DROP TABLE IF EXISTS digest_summaries")
                // Rebuild pulse_notifications without the removed isSimulated column
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pulse_notifications_new` (" +
                        "`key` TEXT NOT NULL, " +
                        "`notificationId` INTEGER NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`appLabel` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`postTime` INTEGER NOT NULL, " +
                        "`isRead` INTEGER NOT NULL, " +
                        "`isDismissed` INTEGER NOT NULL, " +
                        "`canReply` INTEGER NOT NULL, " +
                        "`secondaryActionsJson` TEXT NOT NULL, " +
                        "`repliesJson` TEXT NOT NULL, " +
                        "`urlsJson` TEXT NOT NULL, " +
                        "`sourceChannel` TEXT NOT NULL, " +
                        "`isStarred` INTEGER NOT NULL, " +
                        "`reminderAt` INTEGER, " +
                        "`reminderNote` TEXT, " +
                        "PRIMARY KEY(`key`))"
                )
                db.execSQL(
                    "INSERT INTO pulse_notifications_new (" +
                        "`key`, notificationId, packageName, appLabel, title, body, postTime, " +
                        "isRead, isDismissed, canReply, secondaryActionsJson, repliesJson, " +
                        "urlsJson, sourceChannel, isStarred, reminderAt, reminderNote) " +
                        "SELECT `key`, notificationId, packageName, appLabel, title, body, postTime, " +
                        "isRead, isDismissed, canReply, secondaryActionsJson, repliesJson, " +
                        "urlsJson, sourceChannel, isStarred, reminderAt, reminderNote " +
                        "FROM pulse_notifications"
                )
                db.execSQL("DROP TABLE pulse_notifications")
                db.execSQL("ALTER TABLE pulse_notifications_new RENAME TO pulse_notifications")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pulse_notifications_postTime` ON `pulse_notifications` (`postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pulse_notifications_packageName` ON `pulse_notifications` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pulse_notifications_isStarred` ON `pulse_notifications` (`isStarred`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pulse_notifications ADD COLUMN imageUri TEXT")
            }
        }

        fun getInstance(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "nepho_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
