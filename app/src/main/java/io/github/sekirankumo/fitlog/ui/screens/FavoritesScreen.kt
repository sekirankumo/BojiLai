package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch

/**
 * 常吃食物管理：只能修改和删除。
 * 常吃只在记录饮食时添加（确认食物时勾选「加入常吃」），这里不单独做添加入口。
 */
@Composable
fun FavoritesScreen(container: AppContainer) {
    val c = Brand.colors
    val dao = container.database.foodDao()
    val flow = remember { dao.observeFavorites() }
    val favorites by flow.collectAsState(initial = null)
    var editing by remember { mutableStateOf<FavoriteFood?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun delete(f: FavoriteFood) {
        scope.launch {
            dao.deleteFavorite(f.id)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("已删除「${f.name}」", actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) dao.insertFavorite(f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "常吃在记录饮食时添加：确认食物时勾选「加入常吃」。这里可以修改，左滑删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            val list = favorites
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.StarBorder,
                        "还没有常吃食物",
                        "记录饮食时勾选「加入常吃」，下次在「记一餐」里点一下就能加入。",
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { f ->
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            delete(f)
                            true
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.dangerSoft).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = c.danger)
                        }
                    },
                ) {
                    FavoriteRow(f) { editing = f }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    editing?.let { f ->
        FavoriteEditDialog(
            favorite = f,
            nameTaken = { name -> favorites.orEmpty().any { it.id != f.id && it.name == name } },
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { dao.insertFavorite(updated) }
            },
            onDelete = {
                editing = null
                delete(f)
            },
        )
    }
}

@Composable
private fun FavoriteRow(f: FavoriteFood, onClick: () -> Unit) {
    val c = Brand.colors
    BCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                f.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${f.kcal.fmt(0)} kcal", style = MaterialTheme.typography.titleMedium, color = c.brand)
        }
        val portion = listOfNotNull(f.amount, f.grams?.let { "${it.fmt(0)}g" }).joinToString(" · ")
        Text(
            listOfNotNull(
                portion.ifEmpty { null },
                "蛋白 ${f.proteinG.fmt()}g 脂肪 ${f.fatG.fmt()}g 碳水 ${f.carbsG.fmt()}g",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
        )
        if (f.useCount > 0) {
            Text("用过 ${f.useCount} 次", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
    }
}

/** 编辑常吃：名称、份量、克数（改克数时营养按比例换算）、营养 */
@Composable
private fun FavoriteEditDialog(
    favorite: FavoriteFood,
    nameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (FavoriteFood) -> Unit,
    onDelete: () -> Unit,
) {
    val c = Brand.colors
    var draft by remember(favorite.id) { mutableStateOf(FoodDraft.fromFavorite(favorite, System.currentTimeMillis())) }
    val name = draft.name.trim()
    val error = when {
        name.isEmpty() -> "名称不能为空"
        nameTaken(name) -> "已经有叫「$name」的常吃了，换个名字吧"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("编辑常吃") },
        text = {
            Column {
                FoodDraftEditor(draft, { draft = it }, showFavorite = false, showMealTime = false)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    onSave(
                        favorite.copy(
                            name = name,
                            amount = draft.amount?.trim()?.ifEmpty { null },
                            grams = draft.grams,
                            kcal = draft.kcal,
                            proteinG = draft.proteinG,
                            fatG = draft.fatG,
                            carbsG = draft.carbsG,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = c.danger) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
