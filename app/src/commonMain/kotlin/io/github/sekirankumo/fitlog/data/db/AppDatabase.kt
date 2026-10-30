package io.github.sekirankumo.fitlog.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
        NutritionDay::class,
    ],
    version = 5,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun opLogDao(): OpLogDao
    abstract fun foodDao(): FoodDao
    abstract fun planDao(): PlanDao
    abstract fun kvDao(): KeyValueDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        /** App 里文件名沿用改名前的 fitlog.db，不能改，否则旧数据读不到 */
        const val FILE_NAME = "fitlog.db"
    }
}

// Room 在各平台生成实现
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** 一次表结构升级：从 from 到 to 依次执行这些 SQL（安卓和 iOS 共用） */
class SqlMigration(val from: Int, val to: Int, val statements: List<String>)

/** 用 SQLite 驱动（iOS）执行的迁移；安卓在 androidMain 里再补上旧接口的写法 */
open class SqlRoomMigration(val sql: SqlMigration) : Migration(sql.from, sql.to) {
    override fun migrate(connection: SQLiteConnection) {
        sql.statements.forEach { connection.execSQL(it) }
    }
}

val MIGRATION_SQL = listOf(
    SqlMigration(
        1, 2,
        listOf(
            "CREATE TABLE IF NOT EXISTS `op_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`toolName` TEXT NOT NULL, " +
                "`summary` TEXT NOT NULL, " +
                "`undoJson` TEXT, " +
                "`undone` INTEGER NOT NULL)",
        ),
    ),
    SqlMigration(
        2, 3,
        listOf(
            "ALTER TABLE `body_record` ADD COLUMN `externalId` TEXT",
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
                "`note` TEXT)",
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
                "`lastUsedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `plan_day` (" +
                "`date` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`exercisesJson` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`note` TEXT, " +
                "`completedAt` INTEGER, " +
                "PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `kv` (" +
                "`key` TEXT NOT NULL, " +
                "`value` TEXT NOT NULL, " +
                "PRIMARY KEY(`key`))",
        ),
    ),
    // 膳食质量：食材分类和高盐标记，旧记录为 NULL，打开 App 后在后台补算
    SqlMigration(
        3, 4,
        listOf(
            "ALTER TABLE `food_entry` ADD COLUMN `components` TEXT",
            "ALTER TABLE `food_entry` ADD COLUMN `highSalt` INTEGER",
            "ALTER TABLE `favorite_food` ADD COLUMN `components` TEXT",
            "ALTER TABLE `favorite_food` ADD COLUMN `highSalt` INTEGER",
        ),
    ),
    // 每天单独的营养目标：只新建一张表，原有数据不动
    SqlMigration(
        4, 5,
        listOf(
            "CREATE TABLE IF NOT EXISTS `nutrition_day` (" +
                "`date` INTEGER NOT NULL, " +
                "`kcal` INTEGER NOT NULL, " +
                "`proteinG` INTEGER NOT NULL, " +
                "`fatG` INTEGER NOT NULL, " +
                "`carbsG` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "`source` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))",
        ),
    ),
)
