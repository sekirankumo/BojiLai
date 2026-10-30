package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import io.github.sekirankumo.fitlog.ui.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.ListDivider
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.StrikeThrough
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.launch

/** 操作记录：AI 和自动任务做过的所有修改，可以逐条撤销 */
@Composable
fun OperationsScreen(container: AppContainer) {
    val c = Brand.colors
    val toast = LocalToast.current
    val flow = remember { container.database.opLogDao().observeRecent() }
    val logs by flow.collectAsState(initial = null)
    val state = rememberLazyListState()
    TrackTopBar(state)

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        item(key = "intro") {
            Text(
                "AI 助手、语音打卡、自动调整计划做过的修改都在这里，可以撤销。撤销较早的操作时，之后基于它的修改可能会受影响。",
                style = Brand.type.label,
                color = c.muted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
            )
        }
        val items = logs ?: return@LazyColumn
        if (items.isEmpty()) {
            item(key = "empty") { EmptyState(Icons.Outlined.History, "还没有记录", "AI 帮你改了数据之后，会在这里留下记录。") }
        }
        itemsIndexed(items, key = { _, op -> op.id }) { i, op ->
            val top = if (i == 0) 18.dp else 0.dp
            val bottom = if (i == items.lastIndex) 18.dp else 0.dp
            Column(itemMotion().clip(RoundedCornerShape(top, top, bottom, bottom)).background(c.surface)) {
                if (i > 0) ListDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            op.summary,
                            style = Brand.type.bodyMedium,
                            color = if (op.undone) c.muted else c.text,
                            textDecoration = if (op.undone) StrikeThrough else null,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            TimeUtil.format(op.createdAt) + when {
                                op.undone -> " · 已撤销"
                                op.undoJson == null -> " · 不可撤销"
                                else -> ""
                            },
                            style = Brand.type.note,
                            color = c.muted,
                        )
                    }
                    if (!op.undone && op.undoJson != null) {
                        BButton("撤销", kind = ButtonKind.Soft, height = 34.dp) {
                            container.appScope.launch {
                                val message = try {
                                    container.operations.undo(op.id)
                                } catch (e: Exception) {
                                    "撤销失败：${e.message}"
                                }
                                toast.show(message)
                            }
                        }
                    }
                }
            }
        }
    }
}
