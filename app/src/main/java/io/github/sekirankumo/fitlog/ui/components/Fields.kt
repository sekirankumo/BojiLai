package io.github.sekirankumo.fitlog.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.sekirankumo.fitlog.util.TimeUtil
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** 去掉多余小数位：12.0 → "12"，12.34 → "12.3" */
fun Double.clean(): String {
    val r = (this * 10).roundToLong() / 10.0
    return if (abs(r - r.roundToLong()) < 1e-9) r.roundToLong().toString() else r.toString()
}

/** 数字输入框：允许输入过程中的中间状态（如 "12."），外部数值变化时同步；数字用 Barlow 显示 */
@Composable
fun NumberField(
    value: Double?,
    onValue: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
    placeholder: String? = null,
) {
    var text by remember { mutableStateOf(value?.clean() ?: "") }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = value?.clean() ?: ""
    }
    Field(
        value = text,
        onValueChange = { t ->
            text = t
            onValue(t.toDoubleOrNull())
        },
        label = label,
        placeholder = placeholder,
        numeric = true,
        integer = integer,
        modifier = modifier,
    )
}

/** 依次弹出日期和时间选择 */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val local = TimeUtil.toLocal(initial)
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            onPicked(TimeUtil.toEpoch(local.withYear(y).withMonth(m + 1).withDayOfMonth(d).withHour(h).withMinute(min)))
        }, local.hour, local.minute, true).show()
    }, local.year, local.monthValue - 1, local.dayOfMonth).show()
}

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d ->
        onPicked(LocalDate.of(y, m + 1, d))
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

/** 只选时间 */
fun pickTime(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, true).show()
}
