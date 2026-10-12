package io.github.sekirankumo.fitlog.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.sekirankumo.fitlog.util.TimeUtil
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** 去掉多余小数位：12.0 → "12"，12.34 → "12.3" */
fun Double.clean(): String {
    val r = (this * 10).roundToLong() / 10.0
    return if (abs(r - r.roundToLong()) < 1e-9) r.roundToLong().toString() else r.toString()
}

/** 数字输入框：允许输入过程中的中间状态（如 "12."），外部数值变化时同步 */
@Composable
fun NumberField(
    value: Double?,
    onValue: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
) {
    var text by remember { mutableStateOf(value?.clean() ?: "") }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = value?.clean() ?: ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            onValue(t.toDoubleOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
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
