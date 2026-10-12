package io.github.sekirankumo.fitlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地数据库。每次改表结构都要把 version + 1 并在 MIGRATIONS 里写迁移，
 * 否则远程更新后会丢数据。
 */
@Database(
    entities = [BodyRecord::class, OpLog::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun opLogDao(): OpLogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitlog.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

private val MIGRATIONS = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `op_log` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`toolName` TEXT NOT NULL, " +
                    "`summary` TEXT NOT NULL, " +
                    "`undoJson` TEXT, " +
                    "`undone` INTEGER NOT NULL)"
            )
        }
    },
)
