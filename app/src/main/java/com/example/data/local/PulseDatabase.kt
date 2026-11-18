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
        ExtractedLinkEntity::class,
        DigestSummaryEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "nepho_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
