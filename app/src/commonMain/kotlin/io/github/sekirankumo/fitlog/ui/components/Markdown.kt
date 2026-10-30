package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.sekirankumo.fitlog.ui.theme.Brand

/** Markdown 里的一块内容 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListBlock(val items: List<MdListItem>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val header: List<String>, val aligns: List<TextAlign>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

/** 列表项：marker 为 null 是无序列表，否则是「1.」这样的序号 */
data class MdListItem(val depth: Int, val marker: String?, val text: String)

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val RULE = Regex("""^([-*_])(\s*\1){2,}$""")
private val LIST = Regex("""^(\s*)([-*+]|\d{1,3}[.)、])\s+(.*)$""")
private val TABLE_SEP = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?$""")

/**
 * 解析 AI 回复里常见的 Markdown：标题、段落、列表、引用、代码块、分隔线、表格。
 * 不追求完整的 CommonMark，只保证常见写法显示正常、怎么写都不会出错。
 */
fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<MdBlock>()
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) {
            out += MdBlock.Paragraph(para.joinToString("\n"))
            para.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.isEmpty() -> {
                flush()
                i++
            }
            t.startsWith("```") || t.startsWith("~~~") -> {
                flush()
                val fence = t.take(3)
                val body = mutableListOf<String>()
                i++
                // 没有结束标记时一直到结尾
                while (i < lines.size && !lines[i].trim().startsWith(fence)) body += lines[i++]
                i++
                out += MdBlock.Code(body.joinToString("\n").trimEnd())
            }
            HEADING.matches(t) -> {
                flush()
                val m = HEADING.find(t)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
                i++
            }
            RULE.matches(t) -> {
                flush()
                out += MdBlock.Rule
                i++
            }
            '|' in t && i + 1 < lines.size && lines[i + 1].trim().let { '|' in it && '-' in it && TABLE_SEP.matches(it) } -> {
                flush()
                val header = tableCells(t)
                val aligns = tableCells(lines[i + 1].trim()).map { cell ->
                    when {
                        cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
                        cell.endsWith(":") -> TextAlign.End
                        else -> TextAlign.Start
                    }
                }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().let { it.isNotEmpty() && '|' in it }) {
                    rows += tableCells(lines[i].trim())
                    i++
                }
                val n = header.size
                out += MdBlock.Table(
                    header,
                    List(n) { aligns.getOrElse(it) { TextAlign.Start } },
                    rows.map { r -> List(n) { r.getOrElse(it) { "" } } },
                )
            }
            t.startsWith(">") -> {
                flush()
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    body += lines[i].trim().removePrefix(">").trimStart()
                    i++
                }
                out += MdBlock.Quote(body.joinToString("\n"))
            }
            LIST.matches(line) -> {
                flush()
                val raw = mutableListOf<Triple<Int, String?, String>>()
                while (i < lines.size) {
                    val l = lines[i]
                    val m = LIST.find(l)
                    when {
                        m != null -> {
                            val marker = m.groupValues[2]
                            raw += Triple(
                                m.groupValues[1].replace("\t", "    ").length,
                                if (marker[0].isDigit()) marker.trimEnd('、', ')').trimEnd('.') + "." else null,
                                m.groupValues[3],
                            )
                            i++
                        }
                        // 列表项的续行
                        l.isNotBlank() && l.startsWith("  ") && raw.isNotEmpty() -> {
                            val last = raw.removeAt(raw.lastIndex)
                            raw += last.copy(third = last.third + "\n" + l.trim())
                            i++
                        }
                        // 列表项之间的空行
                        l.isBlank() && i + 1 < lines.size && LIST.matches(lines[i + 1]) -> i++
                        else -> break
                    }
                }
                // 缩进有人用 2 个空格、有人用 4 个，按出现过的缩进排序来定层级
                val levels = raw.map { it.first }.distinct().sorted()
                out += MdBlock.ListBlock(raw.map { (indent, marker, text) ->
                    MdListItem(levels.indexOf(indent).coerceAtMost(3), marker, text)
                })
            }
            else -> {
                para += t
                i++
            }
        }
    }
    flush()
    return out
}

private fun tableCells(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
    return s.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
}

/**
 * 行内格式。各分组依次是：
 * 1 行内代码、2 粗体、3 粗体（下划线写法）、4 删除线、5 斜体、6/7 链接文字和地址、8 裸网址、9 转义字符；
 * 另外 <br> 换成换行（AI 常在表格里用它换行）
 */
private val INLINE = Regex(
    """`([^`\n]+)`""" +
        """|\*\*(.+?)\*\*""" +
        """|__(.+?)__""" +
        """|~~(.+?)~~""" +
        """|(?<![*\w])\*(?![\s*])(.+?)(?<![\s*])\*(?!\*)""" +
        """|\[([^\]\n]+)]\(([^)\s]+)\)""" +
        """|(https?://[^\s<>()（）\[\]，。！？；：、“”‘’]+)""" +
        """|\\([\\`*_~\[\]()#|>-])""" +
        """|<br\s*/?>""",
    RegexOption.IGNORE_CASE,
)

private class InlineStyles(val code: SpanStyle, val link: TextLinkStyles)

private fun inline(text: String, styles: InlineStyles): AnnotatedString = buildAnnotatedString { appendInline(text, styles) }

private fun AnnotatedString.Builder.appendInline(s: String, styles: InlineStyles) {
    var i = 0
    while (i < s.length) {
        val m = INLINE.find(s, i)
        if (m == null) {
            append(s.substring(i))
            return
        }
        append(s.substring(i, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(styles.code) { append(g[1]) }
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(g[2], styles) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(g[3], styles) }
            g[4].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(g[4], styles) }
            g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[5], styles) }
            g[6].isNotEmpty() -> {
                val url = g[7]
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    withLink(LinkAnnotation.Url(url, styles.link)) { appendInline(g[6], styles) }
                } else {
                    appendInline(g[6], styles)
                }
            }
            g[8].isNotEmpty() -> withLink(LinkAnnotation.Url(g[8], styles.link)) { append(g[8]) }
            g[9].isNotEmpty() -> append(g[9])
            else -> append("\n")
        }
        i = m.range.last + 1
    }
}

/** 把 Markdown 文本显示成排好版的内容 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Brand.colors.text) {
    val blocks = remember(text) { parseMarkdown(text) }
    MarkdownBlocks(blocks, modifier, color)
}

@Composable
fun MarkdownBlocks(blocks: List<MdBlock>, modifier: Modifier = Modifier, color: Color = Brand.colors.text) {
    val c = Brand.colors
    val styles = remember(c) {
        InlineStyles(
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.surface2, fontSize = 0.9.em),
            link = TextLinkStyles(SpanStyle(color = c.brand, textDecoration = TextDecoration.Underline)),
        )
    }
    val body = MaterialTheme.typography.bodyLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, styles),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                is MdBlock.Paragraph -> Text(inline(block.text, styles), style = body, color = color)
                is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(Modifier.padding(start = 16.dp * item.depth)) {
                            Text(
                                item.marker ?: if (item.depth == 0) "•" else "◦",
                                style = body,
                                color = if (item.marker == null) c.brand else c.muted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 16.dp).padding(end = 6.dp),
                            )
                            Text(inline(taskBox(item.text), styles), style = body, color = color, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.line, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(inline(block.text, styles), style = body, color = c.muted)
                }
                is MdBlock.Code -> Surface(color = c.surface2, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
                is MdBlock.Table -> MdTable(block, styles, color)
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.line)
            }
        }
    }
}

private fun taskBox(text: String): String = when {
    text.startsWith("[ ] ") -> "☐ " + text.drop(4)
    text.startsWith("[x] ", ignoreCase = true) -> "☑ " + text.drop(4)
    else -> text
}

/** 表格：列宽按内容自动分配，放不下时可以左右滑动 */
@Composable
private fun MdTable(table: MdBlock.Table, styles: InlineStyles, color: Color) {
    val c = Brand.colors
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        Box(Modifier.clip(shape).border(1.dp, c.line, shape)) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                TableLayout(table, styles, color, minTotalWidth = available)
            }
        }
    }
}

@Composable
private fun TableLayout(table: MdBlock.Table, styles: InlineStyles, color: Color, minTotalWidth: Dp) {
    val c = Brand.colors
    val cols = table.header.size
    val rows = listOf(table.header) + table.rows
    Layout(content = {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { col, cell ->
                val header = r == 0
                val lastCol = col == cols - 1
                val lastRow = r == rows.size - 1
                Box(
                    Modifier
                        .background(if (header) c.surface2 else Color.Transparent)
                        .drawBehind {
                            val s = 1.dp.toPx()
                            if (!lastCol) drawLine(c.line, Offset(size.width - s / 2, 0f), Offset(size.width - s / 2, size.height), s)
                            if (!lastRow) drawLine(c.line, Offset(0f, size.height - s / 2), Offset(size.width, size.height - s / 2), s)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        inline(cell, styles),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (header) FontWeight.SemiBold else null,
                        color = color,
                        textAlign = table.aligns[col],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }) { measurables, _ ->
        val minCol = 44.dp.roundToPx()
        val maxCol = 200.dp.roundToPx()
        // 每列取内容最宽的一格，限制在 44–200dp，超出的文字换行
        val widths = IntArray(cols) { j ->
            rows.indices.maxOf { r -> measurables[r * cols + j].maxIntrinsicWidth(Constraints.Infinity) }.coerceIn(minCol, maxCol)
        }
        // 总宽度不够一屏时按比例拉宽铺满
        val target = minTotalWidth.roundToPx() - 2.dp.roundToPx()
        val total = widths.sum()
        if (total in 1 until target) {
            val extra = target - total
            var given = 0
            for (j in 0 until cols) {
                val add = if (j == cols - 1) extra - given else extra * widths[j] / total
                widths[j] += add
                given += add
            }
        }
        val heights = IntArray(rows.size) { r ->
            (0 until cols).maxOf { j -> measurables[r * cols + j].minIntrinsicHeight(widths[j]) }
        }
        val placeables = measurables.mapIndexed { idx, m ->
            m.measure(Constraints.fixed(widths[idx % cols], heights[idx / cols]))
        }
        layout(widths.sum(), heights.sum()) {
            var y = 0
            for (r in rows.indices) {
                var x = 0
                for (j in 0 until cols) {
                    placeables[r * cols + j].place(x, y)
                    x += widths[j]
                }
                y += heights[r]
            }
        }
    }
}
