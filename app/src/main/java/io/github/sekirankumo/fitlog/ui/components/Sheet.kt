package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch

/** 弹层里的操作：close 先播完收起动画，再关掉弹层，然后执行 then */
class SheetScope internal constructor(private val hideThen: (() -> Unit) -> Unit) {
    fun close(then: () -> Unit = {}) = hideThen(then)
}

/**
 * 底部弹层（代替原来的对话框）：从底部弹起，可以往下拉关掉；里面的输入框、次要按钮用弹层的配色。
 * @param dismissible 为 false 时不能下拉或点外面关掉（比如正在识别最后一句语音）
 * @param actions 底部一排按钮，用 Modifier.weight(1f) 平分宽度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    dismissible: Boolean = true,
    actions: (@Composable RowScope.(SheetScope) -> Unit)? = null,
    content: @Composable ColumnScope.(SheetScope) -> Unit = {},
) {
    val c = Brand.colors
    val canDismiss by rememberUpdatedState(dismissible)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { canDismiss || it != SheetValue.Hidden },
    )
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val toast = LocalToast.current
    LaunchedEffect(Unit) { toast.dismiss() }
    val sheetScope = remember {
        SheetScope { then ->
            scope.launch { state.hide() }.invokeOnCompletion {
                dismiss()
                then()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) dismiss() },
        sheetState = state,
        containerColor = c.sheet,
        contentColor = c.text,
        scrimColor = Color(0x570A0F0C),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.faint.copy(alpha = 0.5f)))
        },
    ) {
        OnSheet {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                title?.let { Text(it, style = Brand.type.h2, color = c.text) }
                subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = Brand.type.label, color = c.muted)
                }
                if (title != null || subtitle != null) Spacer(Modifier.height(16.dp))
                content(sheetScope)
                actions?.let { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { row(sheetScope) }
                }
            }
        }
    }
}

/** 二次确认：标题、说明、取消 + 确认 */
@Composable
fun ConfirmSheet(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    onConfirm: () -> Unit,
) {
    BSheet(
        onDismiss = onDismiss,
        title = title,
        actions = { s ->
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            BButton(confirm, Modifier.weight(1f), kind = if (danger) ButtonKind.Danger else ButtonKind.Primary) { s.close(onConfirm) }
        },
    ) {
        Text(text, style = Brand.type.body, color = Brand.colors.text)
    }
}
