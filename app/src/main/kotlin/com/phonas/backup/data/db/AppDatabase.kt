package com.phonas.backup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.dao.BackupLogDao
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.db.entity.LogStatus

class AppTypeConverters {
    @TypeConverter
    fun fromBackupStatus(status: BackupStatus): String = status.name

    @TypeConverter
    fun toBackupStatus(value: String): BackupStatus = BackupStatus.valueOf(value)

    @TypeConverter
    fun fromLogStatus(status: LogStatus): String = status.name

    @TypeConverter
    fun toLogStatus(value: String): LogStatus = LogStatus.valueOf(value)
}

@Database(
    entities = [BackupFileRecord::class, BackupLogEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun backupFileDao(): BackupFileDao
    abstract fun backupLogDao(): BackupLogDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "backup.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
