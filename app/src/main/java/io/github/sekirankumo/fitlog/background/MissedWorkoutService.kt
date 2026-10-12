package io.github.sekirankumo.fitlog.background

import android.content.Context
import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolRegistry
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.data.plan.PlanRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * 漏打卡检查：把过去计划了但没做的训练标为「漏打卡」，然后让 AI 自动重新安排接下来两周，
 * 直接生效并写入操作日志（可在对话或「操作记录」里撤销）。
 */
class MissedWorkoutService(
    private val context: Context,
    private val plans: PlanRepository,
    private val kv: KeyValueDao,
    private val operations: OperationService,
    private val registry: ToolRegistry,
) {
    private val mutex = Mutex()

    /** 返回调整说明；没有漏打卡返回 null */
    suspend fun check(): String? = mutex.withLock {
        val yesterday = LocalDate.now().minusDays(1)
        val checked = kv.get(KEY)?.toLongOrNull()?.let { LocalDate.ofEpochDay(it) }
            ?: yesterday.minusDays(1) // 第一次运行只检查昨天
        if (!checked.isBefore(yesterday)) return null

        val candidates = plans.range(checked.plusDays(1), yesterday).filter { it.status == DayStatus.PLANNED }
        val missed = mutableListOf<String>()
        for (day in candidates) {
            val anyDone = plans.exercisesOf(day).any { ex -> ex.sets.any { it.done } }
            // 做了一部分也算练过
            val status = if (anyDone) DayStatus.DONE else DayStatus.MISSED
            plans.upsert(day.copy(status = status))
            if (!anyDone) missed += "${LocalDate.ofEpochDay(day.date)} ${day.title}"
        }
        kv.put(KeyValue(KEY, yesterday.toEpochDay().toString()))
        if (missed.isEmpty()) return null

        val tool = registry["replan_training"] ?: return null
        val args = buildJsonObject {
            put("reason", "漏打卡：${missed.joinToString("、")} 没有完成。请把漏掉的训练合理安排进后面的计划。")
            put("days_ahead", 14)
            put("allow_fallback", true)
            put("label", "漏打卡自动调整")
        }
        val summary = runCatching { operations.runWrite(tool, args).first.result }
            .getOrElse { "漏打卡：${missed.joinToString("、")}。自动调整失败（${it.message}），可以稍后让 AI 重新安排。" }
        Notifications.show(context, 1001, Notifications.CHANNEL_PLAN, "训练计划已自动调整", summary, "training")
        summary
    }

    private companion object {
        const val KEY = "missed_checked_through"
    }
}
