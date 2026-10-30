package io.github.sekirankumo.fitlog.util

import kotlinx.datetime.atTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** AI 和界面统一使用的时间格式：yyyy-MM-dd HH:mm（本地时区） */
object TimeUtil {
    private val zone: TimeZone get() = TimeZone.currentSystemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val shortDate = DateTimeFormatter.ofPattern("M月d日")
    private val shortDateTime = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    fun toLocal(epochMs: Long): LocalDateTime = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    fun toEpoch(local: LocalDateTime): Long = local.toInstant(zone).toEpochMilliseconds()

    fun format(epochMs: Long): String = toLocal(epochMs).format(dateTime)
    fun formatDate(epochMs: Long): String = toLocal(epochMs).format(date)
    fun formatShortDate(epochMs: Long): String = toLocal(epochMs).format(shortDate)
    fun formatShort(epochMs: Long): String = toLocal(epochMs).format(shortDateTime)

    private val DATE_TIME = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?""")

    /** 解析 yyyy-MM-dd HH:mm 或 yyyy-MM-dd（取 08:00） */
    fun parse(text: String): Long? {
        val t = text.trim().replace('T', ' ').replace('/', '-')
        val m = DATE_TIME.matchEntire(t.take(16).trim()) ?: DATE_TIME.matchEntire(t.take(10)) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        return runCatching {
            val day = LocalDate(y.toInt(), mo.toInt(), d.toInt())
            toEpoch(if (h.isEmpty()) day.atTime(8, 0) else day.atTime(h.toInt(), mi.toInt()))
        }.getOrNull()
    }

    /** 给 AI 的当前时间描述，例如 2026-10-12 21:40 星期一 */
    fun nowForPrompt(): String {
        val now = LocalDateTime.now()
        return now.format(dateTime) + " " + now.dayOfWeek.zhFull()
    }

    fun daysAgo(days: Long): Long = nowMillis() - days * 24 * 3600 * 1000
}

/** 保留 digits 位小数（四舍五入），和 "%.1f".format(x) 一样 */
fun Double.fmt(digits: Int = 1): String {
    if (isNaN() || isInfinite()) return toString()
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (abs(this) * factor).roundToLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    if (digits == 0) return sign + scaled
    return sign + (scaled / factor) + "." + (scaled % factor).toString().padStart(digits, '0')
}

fun Float.fmt(digits: Int = 1): String = toDouble().fmt(digits)

/** 两位数补零，例如 7 → "07" */
fun Int.pad2(): String = toString().padStart(2, '0')
