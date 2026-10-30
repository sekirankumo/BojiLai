package io.github.sekirankumo.fitlog.ui.components

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.sekirankumo.fitlog.util.TimeUtil
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

