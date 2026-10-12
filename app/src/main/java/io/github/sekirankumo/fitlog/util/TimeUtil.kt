package io.github.sekirankumo.fitlog.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** AI 和界面统一使用的时间格式：yyyy-MM-dd HH:mm（本地时区） */
object TimeUtil {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val shortDate = DateTimeFormatter.ofPattern("M月d日")
    private val shortDateTime = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    fun toLocal(epochMs: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    fun toEpoch(local: LocalDateTime): Long = local.atZone(zone).toInstant().toEpochMilli()

    fun format(epochMs: Long): String = toLocal(epochMs).format(dateTime)
    fun formatDate(epochMs: Long): String = toLocal(epochMs).format(date)
    fun formatShortDate(epochMs: Long): String = toLocal(epochMs).format(shortDate)
    fun formatShort(epochMs: Long): String = toLocal(epochMs).format(shortDateTime)

    /** 解析 yyyy-MM-dd HH:mm 或 yyyy-MM-dd（取 08:00） */
    fun parse(text: String): Long? {
        val t = text.trim().replace('T', ' ').replace('/', '-')
        return runCatching { toEpoch(LocalDateTime.parse(t.take(16), dateTime)) }.getOrNull()
            ?: runCatching { toEpoch(LocalDate.parse(t.take(10), date).atTime(8, 0)) }.getOrNull()
    }

    /** 给 AI 的当前时间描述，例如 2026-10-12 21:40 星期一 */
    fun nowForPrompt(): String {
        val now = LocalDateTime.now(zone)
        return now.format(dateTime) + " " + now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
    }

    fun daysAgo(days: Long): Long = System.currentTimeMillis() - days * 24 * 3600 * 1000
}

fun Double.fmt(digits: Int = 1): String = "%.${digits}f".format(this)
