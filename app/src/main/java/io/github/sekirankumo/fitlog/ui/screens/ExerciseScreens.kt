package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.exercise.MUSCLE_GROUPS

/** 动作库：搜索 + 按肌群筛选 */
@Composable
fun ExerciseLibraryScreen(repo: ExerciseRepository, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = MUSCLE_GROUPS.keys.toList() + listOf("有氧", "拉伸")
    val list = repo.search(query.ifBlank { null }, group)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索动作，例如：深蹲、卧推") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEach { g ->
                FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(list, key = { it.id }) { e ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(e.id) },
                    leadingContent = { ExerciseThumb(e) },
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text("${e.equipment} · ${e.primary.joinToString("、")}") },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ExerciseThumb(e: Exercise?, size: Int = 56) {
    val uri = e?.imageUris?.firstOrNull()
    if (uri != null) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** 动作详情：两张示意图 + 中文步骤 */
@Composable
fun ExerciseDetailScreen(exercise: Exercise?) {
    if (exercise == null) {
        Text("找不到这个动作", Modifier.padding(16.dp))
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            exercise.nameEn,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.imageUris.forEachIndexed { i, uri ->
                Column(Modifier.weight(1f)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = if (i == 0) "起始姿势" else "结束姿势",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(
                        if (i == 0) "起始" else "结束",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        InfoLine("器械", exercise.equipment)
        InfoLine("主要肌群", exercise.primary.joinToString("、"))
        if (exercise.secondary.isNotEmpty()) InfoLine("辅助肌群", exercise.secondary.joinToString("、"))
        exercise.level?.let { InfoLine("难度", it) }
        Text("动作要领", style = MaterialTheme.typography.titleMedium)
        exercise.steps.forEachIndexed { i, s ->
            Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "图片来源：free-exercise-db（公共领域）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
