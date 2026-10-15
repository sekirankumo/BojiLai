package io.github.sekirankumo.fitlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地数据库。每次改表结构都要把 version + 1 并在 MIGRATIONS 里写迁移，
 * 否则远程更新后会丢数据。建表语句以 CI 导出的 app/schemas 为准。
 */
@Database(
    entities = [
        BodyRecord::class,
        OpLog::class,
        FoodEntry::class,
        FavoriteFood::class,
        PlanDay::class,
        KeyValue::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun opLogDao(): OpLogDao
    abstract fun foodDao(): FoodDao
    abstract fun planDao(): PlanDao
    abstract fun kvDao(): KeyValueDao

    companion object {
        fun create(context: Context): AppDatabase =
            // 文件名沿用改名前的 fitlog.db，不能改，否则旧数据读不到
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
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `body_record` ADD COLUMN `externalId` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `food_entry` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`eatenAt` INTEGER NOT NULL, " +
                    "`meal` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`amount` TEXT, " +
                    "`grams` REAL, " +
                    "`kcal` REAL NOT NULL, " +
                    "`proteinG` REAL NOT NULL, " +
                    "`fatG` REAL NOT NULL, " +
                    "`carbsG` REAL NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`note` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite_food` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`amount` TEXT, " +
                    "`grams` REAL, " +
                    "`kcal` REAL NOT NULL, " +
                    "`proteinG` REAL NOT NULL, " +
                    "`fatG` REAL NOT NULL, " +
                    "`carbsG` REAL NOT NULL, " +
                    "`useCount` INTEGER NOT NULL, " +
                    "`lastUsedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_day` (" +
                    "`date` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`exercisesJson` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`completedAt` INTEGER, " +
                    "PRIMARY KEY(`date`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `kv` (" +
                    "`key` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "PRIMARY KEY(`key`))"
            )
        }
    },
    // 膳食质量：食材分类和高盐标记，旧记录为 NULL，打开 App 后在后台补算
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `components` TEXT")
            db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `highSalt` INTEGER")
            db.execSQL("ALTER TABLE `favorite_food` ADD COLUMN `components` TEXT")
            db.execSQL("ALTER TABLE `favorite_food` ADD COLUMN `highSalt` INTEGER")
        }
    },
)
