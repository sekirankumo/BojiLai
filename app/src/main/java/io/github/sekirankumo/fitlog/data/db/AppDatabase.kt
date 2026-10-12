package io.github.sekirankumo.fitlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地数据库。每次改表结构都要把 version + 1 并写迁移（或 AutoMigration），
 * 否则远程更新后会丢数据。表结构快照导出在 app/schemas/ 下。
 */
@Database(
    entities = [BodyRecord::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bodyRecordDao(): BodyRecordDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitlog.db")
                .build()
    }
}
