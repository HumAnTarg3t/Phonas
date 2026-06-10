package com.phonas.backup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.dao.BackupLogDao
import com.phonas.backup.data.db.dao.BackupSessionFileDao
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.BackupSessionFile
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.db.entity.LogStatus
import com.phonas.backup.data.db.entity.SessionFileStatus

class AppTypeConverters {
    @TypeConverter
    fun fromBackupStatus(status: BackupStatus): String = status.name
    @TypeConverter
    fun toBackupStatus(value: String): BackupStatus = BackupStatus.valueOf(value)

    @TypeConverter
    fun fromLogStatus(status: LogStatus): String = status.name
    @TypeConverter
    fun toLogStatus(value: String): LogStatus = LogStatus.valueOf(value)

    @TypeConverter
    fun fromSessionFileStatus(status: SessionFileStatus): String = status.name
    @TypeConverter
    fun toSessionFileStatus(value: String): SessionFileStatus = SessionFileStatus.valueOf(value)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `backup_session_files` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `logId` INTEGER NOT NULL,
                `filename` TEXT NOT NULL,
                `nasPath` TEXT NOT NULL,
                `actionStatus` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `errorMessage` TEXT,
                FOREIGN KEY (`logId`) REFERENCES `backup_logs`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_session_files_logId` ON `backup_session_files` (`logId`)")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `backup_session_files` ADD COLUMN `localUri` TEXT")
    }
}

@Database(
    entities = [BackupFileRecord::class, BackupLogEntry::class, BackupSessionFile::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun backupFileDao(): BackupFileDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun backupSessionFileDao(): BackupSessionFileDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "backup.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
