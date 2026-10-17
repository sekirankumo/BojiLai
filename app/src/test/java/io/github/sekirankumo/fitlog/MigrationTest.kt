package io.github.sekirankumo.fitlog

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.NutritionDay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 升级不能丢数据：用第 3 版的表结构（app/schemas 里导出的）建一个旧数据库并写入数据，
 * 再用现在的 App 打开，走完所有迁移后，原来的数据都要在，新表也能用。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApp::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun upgradeFromV3KeepsAllData() = runBlocking {
        context.deleteDatabase(NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V3_TABLES.forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO body_record (measuredAt, weightKg, bodyFatPct, source) VALUES (1700000000000, 72.5, 19.2, 'manual')")
            execSQL(
                "INSERT INTO food_entry (eatenAt, meal, name, amount, grams, kcal, proteinG, fatG, carbsG, source) " +
                    "VALUES (1700000000000, 'lunch', '牛肉面', '一碗', 500, 625, 30, 20, 80, 'ai')",
            )
            execSQL("INSERT INTO favorite_food (name, kcal, proteinG, fatG, carbsG, useCount, lastUsedAt) VALUES ('鸡胸肉沙拉', 320, 35, 12, 15, 8, 0)")
            execSQL("INSERT INTO plan_day (date, title, exercisesJson, status) VALUES (19700, '胸 + 三头', '[]', 'done')")
            execSQL("INSERT INTO kv (`key`, value) VALUES ('profile', '{\"sex\":\"male\"}')")
            execSQL("INSERT INTO op_log (createdAt, toolName, summary, undoJson, undone) VALUES (1, 'add_food_entries', '添加饮食', NULL, 0)")
        }
        helper.close()

        val db = AppDatabase.create(context, NAME)
        try {
            assertEquals(72.5, db.bodyRecordDao().all().single().weightKg!!, 0.001)
            val food = db.foodDao().all().single()
            assertEquals("牛肉面", food.name)
            assertEquals(625.0, food.kcal, 0.001)
            assertNull(food.components) // 第 4 版加的列，旧记录为空，之后后台补算
            assertEquals("鸡胸肉沙拉", db.foodDao().favorites().single().name)
            assertEquals("胸 + 三头", db.planDao().all().single().title)
            assertEquals("{\"sex\":\"male\"}", db.kvDao().get("profile"))
            assertEquals(1, db.opLogDao().all().size)
            // 第 5 版的新表：一开始是空的，可以正常写入
            assertEquals(0, db.nutritionDao().all().size)
            db.nutritionDao().upsertAll(listOf(NutritionDay(19701, 2000, 130, 60, 250, "练腿日", NutritionDay.SOURCE_AI, 0)))
            assertEquals(2000, db.nutritionDao().get(19701)!!.kcal)
        } finally {
            db.close()
            context.deleteDatabase(NAME)
        }
    }

    private companion object {
        const val NAME = "migration-test.db"

        /** 第 3 版的建表语句，抄自 app/schemas/.../3.json */
        val V3_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `body_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `measuredAt` INTEGER NOT NULL, `weightKg` REAL, `bodyFatPct` REAL, `source` TEXT NOT NULL, `note` TEXT, `externalId` TEXT)",
            "CREATE TABLE IF NOT EXISTS `op_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `toolName` TEXT NOT NULL, `summary` TEXT NOT NULL, `undoJson` TEXT, `undone` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eatenAt` INTEGER NOT NULL, `meal` TEXT NOT NULL, `name` TEXT NOT NULL, `amount` TEXT, `grams` REAL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `fatG` REAL NOT NULL, `carbsG` REAL NOT NULL, `source` TEXT NOT NULL, `note` TEXT)",
            "CREATE TABLE IF NOT EXISTS `favorite_food` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` TEXT, `grams` REAL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `fatG` REAL NOT NULL, `carbsG` REAL NOT NULL, `useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `plan_day` (`date` INTEGER NOT NULL, `title` TEXT NOT NULL, `exercisesJson` TEXT NOT NULL, `status` TEXT NOT NULL, `note` TEXT, `completedAt` INTEGER, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `kv` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7658d1e8ba238f2e5350be3b5e403c56')",
        )
    }
}
