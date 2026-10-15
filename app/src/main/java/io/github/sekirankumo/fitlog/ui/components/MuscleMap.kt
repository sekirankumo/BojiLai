package io.github.sekirankumo.fitlog.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * 人体肌肉图。轮廓数据来自 MuscleMap（MIT 许可，github.com/melihcolpan/MuscleMap），
 * 由 tools/build_musclemap.py 转换成 assets/musclemap 下的 JSON 文件。
 */
enum class BodyView(val asset: String) {
    MaleFront("musclemap/male_front.json"),
    MaleBack("musclemap/male_back.json"),
    FemaleFront("musclemap/female_front.json"),
    FemaleBack("musclemap/female_back.json");

    companion object {
        fun front(female: Boolean) = if (female) FemaleFront else MaleFront
        fun back(female: Boolean) = if (female) FemaleBack else MaleBack
    }
}

@Serializable
private data class BodyJson(val viewBox: List<Float>, val parts: List<PartJson>)

@Serializable
private data class PartJson(val slug: String, val paths: List<String>)

private class BodyShape(val x: Float, val y: Float, val w: Float, val h: Float, val parts: List<Pair<String, Path>>)

private object BodyShapeCache {
    private val cache = HashMap<BodyView, BodyShape>()
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun get(context: Context, view: BodyView): BodyShape = cache.getOrPut(view) {
        val data = context.assets.open(view.asset).bufferedReader().use { json.decodeFromString<BodyJson>(it.readText()) }
        val parts = data.parts.flatMap { part ->
            part.paths.map { d -> part.slug to PathParser().parsePathString(d).toPath() }
        }
        BodyShape(data.viewBox[0], data.viewBox[1], data.viewBox[2], data.viewBox[3], parts)
    }
}

/** 不是肌肉的部分（头、手、脚、关节），画成中性底色 */
private val NON_MUSCLE = setOf("head", "hair", "hands", "feet", "knees", "ankles", "tibialis")

/**
 * 刚练过的肌肉是品牌绿，越久越浅；刚练完的部位外面有一圈柔和的绿光（安卓 12 起）。
 * @param intensities 部位 → 0..1，越大颜色越深
 */
@Composable
fun MuscleFigure(view: BodyView, intensities: Map<String, Float>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = remember(view) { BodyShapeCache.get(context, view) }
    val c = Brand.colors
    val base = c.muscle
    val neutral = c.bodyOther
    val hot = c.brand
    val outline = c.surface
    val glowing = intensities.any { it.value > 0.5f }
    Box(modifier) {
        if (glowing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }.blur(14.dp, BlurredEdgeTreatment.Unbounded)) {
                drawShape(shape) { slug -> if ((intensities[slug] ?: 0f) > 0.5f && slug !in NON_MUSCLE) hot else null }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawShape(shape, outline) { slug ->
                if (slug in NON_MUSCLE) {
                    neutral
                } else {
                    val t = intensities[slug] ?: 0f
                    if (t <= 0.02f) base else lerp(base, hot, 0.3f + 0.7f * t.toDouble().pow(0.8).toFloat())
                }
            }
        }
    }
}

private fun DrawScope.drawShape(shape: BodyShape, outline: Color? = null, fillOf: (String) -> Color?) {
    val scale = minOf(size.width / shape.w, size.height / shape.h)
    val dx = (size.width - shape.w * scale) / 2f
    val dy = (size.height - shape.h * scale) / 2f
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-shape.x, -shape.y)
    }) {
        val stroke = Stroke(width = 3f / scale)
        for ((slug, path) in shape.parts) {
            val fill = fillOf(slug) ?: continue
            drawPath(path, fill, style = Fill)
            if (outline != null) drawPath(path, outline, style = stroke)
        }
    }
}
