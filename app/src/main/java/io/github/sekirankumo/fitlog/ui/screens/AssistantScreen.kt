package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.ChatItem

@Composable
fun AssistantScreen(engine: AssistantEngine) {
    val items by engine.items.collectAsState()
    val busy by engine.busy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val speech = rememberSpeechInput("请说") { said -> input = if (input.isBlank()) said else "$input $said" }

    // 从其他页面带过来的预填内容
    val prefill by engine.prefill.collectAsState()
    LaunchedEffect(prefill) {
        prefill?.let {
            input = it
            engine.prefill.value = null
        }
    }

    LaunchedEffect(items.size, busy) {
        val count = items.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item { EmptyHint() }
            }
            items(items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> Bubble(item.text, mine = true)
                    is ChatItem.Assistant -> Bubble(item.text, mine = false)
                    is ChatItem.Action -> ActionCard(item, onUndo = { engine.undo(item) })
                    is ChatItem.Pending -> PendingCard(item, onDecide = engine::decide)
                    is ChatItem.Error -> ErrorCard(item.message)
                }
            }
            if (busy && items.none { it is ChatItem.Pending }) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("思考中…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = speech, enabled = !busy) {
                Icon(Icons.Filled.Mic, contentDescription = "语音输入")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么，或点麦克风") },
                maxLines = 4,
            )
            IconButton(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    engine.send(input)
                    input = ""
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("可以直接对我说：", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        listOf(
            "中午吃了一碗牛肉面，加了个卤蛋",
            "今天早上体重 72.5，体脂 18.2",
            "今天的卧推做不了，肩膀有点疼，换个动作",
            "深蹲 4 组都做完了，60 公斤",
            "下周出差没有器材，把计划改成徒手训练",
            "我这周蛋白质吃够了吗？",
            "撤销刚才的自动调整",
        ).forEach {
            Text("· $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "提示：输入框可以用输入法的语音输入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ActionCard(item: ChatItem.Action, onUndo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                item.summary + if (item.undone) "（已撤销）" else "",
                Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.undone) TextDecoration.LineThrough else null,
            )
            if (item.opId != null && !item.undone) {
                TextButton(onClick = onUndo) { Text("撤销") }
            }
        }
    }
}

@Composable
private fun PendingCard(item: ChatItem.Pending, onDecide: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("AI 想要执行以下修改：", style = MaterialTheme.typography.titleSmall)
            item.previews.forEach {
                Text("· $it", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { onDecide(false) }) { Text("取消") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onDecide(true) }) { Text("确认") }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
