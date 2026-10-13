package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.ChatItem
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.theme.Brand

private val SUGGESTIONS = listOf(
    "中午吃了一碗牛肉面，加了个卤蛋",
    "今天早上体重 72.5，体脂 18.2",
    "今天的卧推做不了，肩膀有点疼，换个动作",
    "深蹲 4 组都做完了，60 公斤",
    "下周出差没有器材，把计划改成徒手训练",
    "我这周蛋白质吃够了吗？",
    "撤销刚才的自动调整",
)

@Composable
fun AssistantScreen(engine: AssistantEngine) {
    val c = Brand.colors
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item { Welcome { input = it } }
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  正在思考…", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }

        Surface(color = c.surface) {
            Column {
                HorizontalDivider(color = c.line)
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = c.surface2, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = speech, enabled = !busy) {
                                Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = c.brand)
                            }
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("说点什么，或点麦克风") },
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val canSend = !busy && input.isNotBlank()
                    Surface(
                        onClick = {
                            engine.send(input)
                            input = ""
                        },
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) c.brand else c.surface2,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend) c.onBrand else c.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(onPick: (String) -> Unit) {
    val c = Brand.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Filled.AutoAwesome, tint = c.onBrand, background = c.brand, size = 64.dp, corner = 22.dp)
        Spacer(Modifier.height(14.dp))
        Text("有什么可以帮你？", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "记饮食、打卡、换动作、调整计划、查数据，\n说一句就行。修改前我会先问你。",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUGGESTIONS.forEach { s ->
                Surface(onClick = { onPick(s) }, shape = RoundedCornerShape(16.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.line)) {
                    Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val c = Brand.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            color = if (mine) c.brand else c.surface,
            border = if (mine) null else androidx.compose.foundation.BorderStroke(1.dp, c.line),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer {
                Text(
                    text,
                    color = if (mine) c.onBrand else c.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(item: ChatItem.Action, onUndo: () -> Unit) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.brandSoft) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
            Text(
                item.summary + if (item.undone) "（已撤销）" else "",
                Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                textDecoration = if (item.undone) TextDecoration.LineThrough else null,
            )
            if (item.opId != null && !item.undone) {
                Surface(onClick = onUndo, shape = RoundedCornerShape(10.dp), color = c.surface) {
                    Text("撤销", style = MaterialTheme.typography.labelLarge, color = c.brand, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun PendingCard(item: ChatItem.Pending, onDecide: (Boolean) -> Unit) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.amber) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("AI 想要执行以下修改：", style = MaterialTheme.typography.titleSmall, color = c.onAmber, fontWeight = FontWeight.Bold)
            item.previews.forEach {
                Text("· $it", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = c.onAmber)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                Surface(
                    onClick = { onDecide(false) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.onAmber.copy(alpha = 0.4f)),
                ) {
                    Text("取消", color = c.onAmber, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = { onDecide(true) }, shape = RoundedCornerShape(12.dp), color = c.brand) {
                    Text("确认", color = c.onBrand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.dangerSoft) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
            Text(message, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.danger)
        }
    }
}
