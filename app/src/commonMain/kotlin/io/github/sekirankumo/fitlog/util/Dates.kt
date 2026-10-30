package io.github.sekirankumo.fitlog.util

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/*
 * 日期小工具：安卓和 iOS 共用 kotlinx-datetime，这里补上代码里习惯用的写法
 * （plusDays、toEpochDay、YearMonth 等，和原来的 java.time 用法一致）。
 */

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun LocalDate.Companion.ofEpochDay(day: Long): LocalDate = fromEpochDays(day.toInt())
fun LocalDate.Companion.ofEpochDay(day: Int): LocalDate = fromEpochDays(day)
fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)

fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()
fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.plusDays(n: Long): LocalDate = plus(n.toInt(), DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = minus(n.toInt(), DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Int): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(n.toInt() * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Int): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Long): LocalDate = minus(n.toInt() * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(n: Int): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n.toInt(), DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Int): LocalDate = minus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Long): LocalDate = minus(n.toInt(), DateTimeUnit.MONTH)
fun LocalDate.plusYears(n: Int): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.plusYears(n: Long): LocalDate = plus(n.toInt(), DateTimeUnit.YEAR)
fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
val LocalDate.monthValue: Int get() = monthNumber
fun LocalDate.lengthOfMonth(): Int = YearMonth.from(this).lengthOfMonth()
fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, monthNumber, day)
fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))
fun LocalDate.format(formatter: DateTimeFormatter): String = formatter.format(this, null)

fun LocalDateTime.Companion.now(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time
val LocalDateTime.monthValue: Int get() = monthNumber
fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.format(formatter: DateTimeFormatter): String = formatter.format(date, time)

/** 星期的中文写法：周一…周日 */
fun DayOfWeek.zhShort(): String = "周" + ZH_DAYS[isoDayNumber - 1]

/** 星期的中文全称：星期一…星期日 */
fun DayOfWeek.zhFull(): String = "星期" + ZH_DAYS[isoDayNumber - 1]

private const val ZH_DAYS = "一二三四五六日"

/** 年月（日历用） */
data class YearMonth(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    fun atDay(day: Int): LocalDate = LocalDate(year, monthValue, day)
    fun atEndOfMonth(): LocalDate = atDay(lengthOfMonth())
    fun lengthOfMonth(): Int = atDay(1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    fun plusMonths(n: Long): YearMonth = from(atDay(1).plus(n.toInt(), DateTimeUnit.MONTH))
    fun plusMonths(n: Int): YearMonth = plusMonths(n.toLong())
    fun minusMonths(n: Long): YearMonth = plusMonths(-n)
    fun minusMonths(n: Int): YearMonth = plusMonths(-n.toLong())
    fun isBefore(other: YearMonth): Boolean = this < other
    fun isAfter(other: YearMonth): Boolean = this > other
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.monthValue })

    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.monthNumber)
        fun now(): YearMonth = from(LocalDate.now())
        fun of(year: Int, month: Int): YearMonth = YearMonth(year, month)
    }
}

/**
 * 简单的日期格式（只支持代码里用到的 y M d H m 和其他原样输出的文字），
 * 例如 "yyyy-MM-dd HH:mm"、"M月d日"。
 */
class DateTimeFormatter private constructor(private val pattern: String) {
    fun format(date: LocalDate?, time: LocalTime?): String {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == ch) n++
            val value: Int? = when (ch) {
                'y' -> date?.year
                'M' -> date?.monthNumber
                'd' -> date?.dayOfMonth
                'H' -> time?.hour ?: 0
                'm' -> time?.minute ?: 0
                else -> null
            }
            if (value != null) {
                out.append(value.toString().padStart(n, '0'))
            } else {
                repeat(n) { out.append(ch) }
            }
            i += n
        }
        return out.toString()
    }

    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern)
    }
}

/** 同一周（周一到周日）里的星期几，和 java.time 的 date.with(DayOfWeek.MONDAY) 一样 */
fun LocalDate.with(day: DayOfWeek): LocalDate = plusDays(day.isoDayNumber - dayOfWeek.isoDayNumber)
