package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.launch

/** 操作记录：AI 和自动任务做过的所有修改，可以逐条撤销 */
@Composable
fun OperationsScreen(container: AppContainer) {
    val flow = remember { container.database.opLogDao().observeRecent() }
    val logs by flow.collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                message ?: "AI 助手、语音打卡、自动调整计划做过的修改都在这里，可以撤销。撤销较早的操作时，之后基于它的修改可能会受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (logs.isEmpty()) {
            item { Text("还没有记录", Modifier.padding(16.dp)) }
        }
        items(logs, key = { it.id }) { op ->
            ListItem(
                headlineContent = {
                    Text(
                        op.summary,
                        textDecoration = if (op.undone) TextDecoration.LineThrough else null,
                    )
                },
                supportingContent = {
                    Text(
                        TimeUtil.format(op.createdAt) + when {
                            op.undone -> " · 已撤销"
                            op.undoJson == null -> " · 不可撤销"
                            else -> ""
                        }
                    )
                },
                trailingContent = {
                    if (!op.undone && op.undoJson != null) {
                        TextButton(onClick = {
                            container.appScope.launch {
                                message = try {
                                    container.operations.undo(op.id)
                                } catch (e: Exception) {
                                    "撤销失败：${e.message}"
                                }
                            }
                        }) { Text("撤销") }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}
