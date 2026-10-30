package io.github.sekirankumo.fitlog.ui.screens

import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.sekirankumo.fitlog.data.diet.encodeComponents
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.HighSaltTag
import io.github.sekirankumo.fitlog.ui.components.ListCard
import io.github.sekirankumo.fitlog.ui.components.ListDivider
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch

/**
 * 常吃食物管理：添加、修改、左滑删除（可以撤销）。
 * 也可以在记录饮食时勾选「加入常吃」，或者让 AI 帮忙增删改。
 */
@Composable
fun FavoritesScreen(container: AppContainer) {
    val c = Brand.colors
    val dao = container.database.foodDao()
    val flow = remember { dao.observeFavorites() }
    val favorites by flow.collectAsState(initial = null)
    var editing by remember { mutableStateOf<FavoriteFood?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val list = rememberLazyListState()
    TrackTopBar(list)

    fun delete(f: FavoriteFood) {
        scope.launch { dao.deleteFavorite(f.id) }
        toast.show("已删除「${f.name}」", "撤销") { scope.launch { dao.insertFavorite(f) } }
    }

    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        item {
            Text(
                "这里可以添加、修改常吃，左滑删除。记录饮食时勾选「加入常吃」、或者让 AI 帮忙，也能添加。",
                style = Brand.type.label,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item {
            BButton("添加常吃", Modifier.fillMaxWidth().padding(top = 12.dp), kind = ButtonKind.Soft, icon = AppIcons.Plus) { adding = true }
        }
        val items = favorites
        if (items != null && items.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.StarOutline,
                    "还没有常吃食物",
                    "添加之后，下次在「记一餐」里点一下就能加入。",
                )
            }
        } else if (items != null) {
            item {
                ListCard(Modifier.padding(top = 14.dp)) {
                    items.forEachIndexed { i, f ->
                        if (i > 0) ListDivider()
                        key(f.id) {
                            val state = rememberSwipeToDismissBoxState(
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
                                state = state,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier.fillMaxSize().background(c.danger).padding(end = 22.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text("删除", style = Brand.type.labelBold, color = androidx.compose.ui.graphics.Color.White)
                                    }
                                },
                            ) {
                                FavoriteRow(f) { editing = f }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { f ->
        FavoriteEditSheet(
            favorite = f,
            nameTaken = { name -> favorites.orEmpty().any { it.id != f.id && it.name == name } },
            onDismiss = { editing = null },
            onSave = { updated -> scope.launch { dao.insertFavorite(updated) } },
            onDelete = { delete(f) },
        )
    }
    if (adding) {
        FavoriteEditSheet(
            favorite = null,
            nameTaken = { name -> favorites.orEmpty().any { it.name == name } },
            onDismiss = { adding = false },
            onSave = { food ->
                scope.launch { dao.insertFavorite(food) }
                toast.show("已添加「${food.name}」")
            },
            onDelete = {},
        )
    }
}

@Composable
private fun FavoriteRow(f: FavoriteFood, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, style = Brand.type.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (f.highSalt == true) {
                    Spacer(Modifier.width(6.dp))
                    HighSaltTag()
                }
            }
            Spacer(Modifier.height(3.dp))
            val portion = listOfNotNull(f.amount, f.grams?.let { "${it.fmt(0)}g" }).joinToString(" · ")
            Text(
                listOfNotNull(portion.ifEmpty { null }, "蛋白 ${f.proteinG.fmt()}g 脂肪 ${f.fatG.fmt()}g 碳水 ${f.carbsG.fmt()}g").joinToString(" · "),
                style = Brand.type.note,
                color = c.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
            Text(f.kcal.fmt(0), style = Brand.type.n3, color = c.text)
            if (f.useCount > 0) Text("用过 ${f.useCount} 次", style = Brand.type.note, color = c.muted)
        }
    }
}

/** 添加 / 编辑常吃：名称、份量、克数（改克数时营养按比例换算）、营养。favorite 为 null 时是添加 */
@Composable
private fun FavoriteEditSheet(
    favorite: FavoriteFood?,
    nameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (FavoriteFood) -> Unit,
    onDelete: () -> Unit,
) {
    val c = Brand.colors
    val original = remember(favorite?.id) {
        favorite?.let { FoodDraft.fromFavorite(it, nowMillis()) } ?: FoodDraft(
            key = FoodDraft.nextKey(),
            name = "",
            amount = null,
            grams = null,
            kcal = 0.0,
            proteinG = 0.0,
            fatG = 0.0,
            carbsG = 0.0,
            meal = Meal.Snack,
            eatenAt = nowMillis(),
            source = "manual",
        )
    }
    var draft by remember(favorite?.id) { mutableStateOf(original) }
    val name = draft.name.trim()
    val error = when {
        name.isEmpty() -> "名称不能为空"
        nameTaken(name) -> "已经有叫「$name」的常吃了，换个名字吧"
        else -> null
    }
    BSheet(
        onDismiss = onDismiss,
        title = if (favorite == null) "添加常吃" else "编辑常吃",
        actions = { s ->
            if (favorite != null) BButton("删除", Modifier.weight(0.8f), kind = ButtonKind.Danger) { s.close(onDelete) }
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            BButton("保存", Modifier.weight(1f), enabled = error == null) {
                if (favorite == null) {
                    // 新加的：食材和高盐交给后台分析（没手动标高盐时留空）
                    val food = FavoriteFood(
                        name = name,
                        amount = draft.amount?.trim()?.ifEmpty { null },
                        grams = draft.grams,
                        kcal = draft.kcal,
                        proteinG = draft.proteinG,
                        fatG = draft.fatG,
                        carbsG = draft.carbsG,
                        components = encodeComponents(draft.components),
                        highSalt = if (draft.components == null && !draft.highSalt) null else draft.highSalt,
                    )
                    s.close { onSave(food) }
                    return@BButton
                }
                val updated = favorite.copy(
                    name = name,
                    amount = draft.amount?.trim()?.ifEmpty { null },
                    grams = draft.grams,
                    kcal = draft.kcal,
                    proteinG = draft.proteinG,
                    fatG = draft.fatG,
                    carbsG = draft.carbsG,
                    // 改了名字而食材没动过：原来的拆分不再对应，清空后让后台重新分析
                    components = if (name != favorite.name && draft.components == original.components) null else encodeComponents(draft.components),
                    highSalt = draft.highSalt,
                )
                s.close { onSave(updated) }
            }
        },
    ) {
        FoodDraftEditor(draft, { draft = it }, showFavorite = false, showMealTime = false)
        error?.let { Text(it, style = Brand.type.note, color = c.danger, modifier = Modifier.padding(top = 8.dp)) }
    }
}

private val RowShape = RoundedCornerShape(0.dp)
