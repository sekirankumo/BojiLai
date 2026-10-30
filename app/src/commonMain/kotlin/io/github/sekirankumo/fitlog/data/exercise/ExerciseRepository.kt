package io.github.sekirankumo.fitlog.data.exercise

import io.github.sekirankumo.fitlog.platform.readResourceFile
import io.github.sekirankumo.fitlog.platform.resourceUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 动作库中的一个动作（数据来自 free-exercise-db，中文说明见 tools/exercises_zh.txt） */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val nameEn: String,
    val category: String,
    val equipment: String,
    val primary: List<String>,
    val secondary: List<String> = emptyList(),
    val level: String? = null,
    val steps: List<String>,
    /** 资源文件（原来的 assets）里的相对路径 */
    val images: List<String> = emptyList(),
) {
    val imageUris: List<String> get() = images.map { resourceUri(it) }

    /** 给 AI 的一行描述 */
    fun brief(): String = "$id|$name|$equipment|${primary.joinToString("、")}"
}

/** 肌群筛选分组 */
val MUSCLE_GROUPS = linkedMapOf(
    "胸" to listOf("胸"),
    "背" to listOf("背阔肌", "中背", "下背", "斜方肌"),
    "肩" to listOf("肩"),
    "手臂" to listOf("肱二头肌", "肱三头肌", "前臂"),
    "腿" to listOf("大腿前侧", "大腿后侧", "小腿", "大腿内侧"),
    "臀" to listOf("臀", "臀中肌"),
    "核心" to listOf("腹肌"),
)

class ExerciseRepository {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<Exercise> by lazy {
        val text = readResourceFile("exercises/exercises.json")?.decodeToString() ?: error("缺少动作库数据")
        json.decodeFromString<List<Exercise>>(text)
    }

    private val byId by lazy { all.associateBy { it.id } }

    operator fun get(id: String?): Exercise? = id?.let { byId[it] }

    /** 按名称精确或模糊查找（AI 有时只给中文名） */
    fun findByName(name: String): Exercise? {
        val n = name.trim()
        return all.firstOrNull { it.name == n || it.nameEn.equals(n, true) }
            ?: all.firstOrNull { it.name.contains(n) || n.contains(it.name) }
    }

    fun resolve(id: String?, name: String?): Exercise? = get(id) ?: name?.let { findByName(it) }

    fun search(query: String? = null, group: String? = null, equipment: String? = null): List<Exercise> {
        val muscles = group?.let { MUSCLE_GROUPS[it] ?: listOf(it) }
        return all.filter { e ->
            (query.isNullOrBlank() || e.name.contains(query, true) || e.nameEn.contains(query, true) || e.id.contains(query, true)) &&
                (muscles == null || e.primary.any { it in muscles } || (group != null && e.category == group)) &&
                (equipment.isNullOrBlank() || e.equipment.contains(equipment))
        }
    }

    /** 给 AI 的完整动作目录 */
    fun catalog(): String = all.joinToString("\n") { it.brief() }
}
