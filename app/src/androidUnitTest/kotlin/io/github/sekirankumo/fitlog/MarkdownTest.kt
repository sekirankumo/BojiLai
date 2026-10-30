package io.github.sekirankumo.fitlog

import androidx.compose.ui.text.style.TextAlign
import io.github.sekirankumo.fitlog.ui.components.MdBlock
import io.github.sekirankumo.fitlog.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun table() {
        val blocks = parseMarkdown(
            """
            对比一下：

            | 动作 | 组数 | 次数 |
            |:--|:-:|--:|
            | 深蹲 | 4 | 8 |
            | 卧推 | 3 |
            """.trimIndent(),
        )
        assertEquals(2, blocks.size)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("动作", "组数", "次数"), table.header)
        assertEquals(listOf(TextAlign.Start, TextAlign.Center, TextAlign.End), table.aligns)
        // 少了的格子补空
        assertEquals(listOf("卧推", "3", ""), table.rows[1])
    }

    @Test
    fun listsAndHeadings() {
        val blocks = parseMarkdown(
            """
            ## 建议
            - 早餐加鸡蛋
              - 两个就够
            - 晚饭多吃鱼
            1. 第一步
            2. 第二步
            ---
            普通段落
            第二行
            """.trimIndent(),
        )
        assertTrue(blocks[0] is MdBlock.Heading)
        val list = blocks[1] as MdBlock.ListBlock
        assertEquals(listOf(0, 1, 0, 0, 0), list.items.map { it.depth })
        assertEquals(listOf(null, null, null, "1.", "2."), list.items.map { it.marker })
        assertTrue(blocks[2] is MdBlock.Rule)
        assertEquals(MdBlock.Paragraph("普通段落\n第二行"), blocks[3])
    }

    @Test
    fun unclosedCodeFence() {
        val blocks = parseMarkdown("看代码：\n```\nval a = 1")
        assertEquals(MdBlock.Code("val a = 1"), blocks[1])
    }

    @Test
    fun plainTextWithPipesIsNotTable() {
        val blocks = parseMarkdown("体重 70 | 体脂 18\n下一行")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }
}
