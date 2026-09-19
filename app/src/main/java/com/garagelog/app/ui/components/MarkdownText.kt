package com.garagelog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the subset of Markdown that Claude's answers actually use: headings, bullet and
 * numbered lists, bold/italic/inline code, links, fenced code, tables and rules.
 *
 * Hand-rolled rather than pulled in as a dependency to match the app's existing no-extra-library
 * stance (the charts are hand-rolled Canvas for the same reason), and because anything it can't
 * parse should degrade to plain readable text rather than fail — this renders a *stream*, so it
 * is routinely handed half a sentence, an unclosed `**`, or a partial table.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block -> RenderBlock(block) }
    }
}

@Composable
private fun ColumnScope.RenderBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inlineAnnotated(block.text),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )

        is MdBlock.Paragraph -> Text(
            text = inlineAnnotated(block.text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        is MdBlock.ListItems -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(modifier = Modifier.padding(start = (item.indent * 14).dp, bottom = 3.dp)) {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(if (block.ordered) 22.dp else 16.dp),
                    )
                    Text(inlineAnnotated(item.text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is MdBlock.Code -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )

        is MdBlock.Rule -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        is MdBlock.Table -> MarkdownTable(block)
    }
}

/** Wide tables scroll inside their own box rather than squeezing every column past legibility. */
@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val columnCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            repeat(columnCount) { column ->
                Text(
                    text = inlineAnnotated(table.header.getOrElse(column) { "" }),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(TABLE_COLUMN_WIDTH),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 5.dp))
        table.rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 5.dp),
            ) {
                repeat(columnCount) { column ->
                    Text(
                        text = inlineAnnotated(row.getOrElse(column) { "" }),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(TABLE_COLUMN_WIDTH),
                    )
                }
            }
        }
    }
}

private val TABLE_COLUMN_WIDTH = 150.dp

@Composable
private fun inlineAnnotated(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(text, linkColor, codeBackground) {
        buildInline(text, linkColor = linkColor, codeBackground = codeBackground)
    }
}

private fun buildInline(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_PATTERN.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() -> withLink(
                LinkAnnotation.Url(
                    url = groups[2],
                    styles = TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) { append(groups[1]) }

            groups[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(groups[3]) }

            groups[4].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
            ) { append(groups[4]) }

            groups[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[5]) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

// Ordered so a link's bracketed label can't be mistaken for emphasis, and so ** binds before *.
private val INLINE_PATTERN = Regex(
    """\[([^\]\n]+)\]\((https?://[^)\s]+)\)""" +
        """|\*\*([^*\n]+)\*\*""" +
        """|`([^`\n]+)`""" +
        """|\*([^*\n]+)\*""",
)

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItems(val ordered: Boolean, val items: List<Item>) : MdBlock {
        data class Item(val text: String, val indent: Int)
    }
    data class Code(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

private fun parseBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
        paragraph.setLength(0)
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val body = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    body.appendLine(lines[index])
                    index++
                }
                index++ // consume the closing fence, or run off the end on an unclosed one
                blocks.add(MdBlock.Code(body.toString().trimEnd()))
                continue
            }

            trimmed.isEmpty() -> flushParagraph()

            trimmed.matches(Regex("""^(-{3,}|\*{3,}|_{3,})$""")) -> {
                flushParagraph()
                blocks.add(MdBlock.Rule)
            }

            HEADING.matches(line) -> {
                flushParagraph()
                val match = HEADING.find(line)!!
                blocks.add(MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim()))
            }

            isTableRow(trimmed) -> {
                flushParagraph()
                val tableLines = mutableListOf<String>()
                while (index < lines.size && isTableRow(lines[index].trim())) {
                    tableLines.add(lines[index].trim())
                    index++
                }
                blocks.add(parseTable(tableLines))
                continue
            }

            BULLET.matches(line) || ORDERED.matches(line) -> {
                flushParagraph()
                val ordered = ORDERED.matches(line)
                val pattern = if (ordered) ORDERED else BULLET
                val items = mutableListOf<MdBlock.ListItems.Item>()
                while (index < lines.size && pattern.matches(lines[index])) {
                    val match = pattern.find(lines[index])!!
                    items.add(
                        MdBlock.ListItems.Item(
                            text = match.groupValues[2].trim(),
                            indent = match.groupValues[1].length / 2,
                        ),
                    )
                    index++
                }
                blocks.add(MdBlock.ListItems(ordered, items))
                continue
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

private fun isTableRow(line: String): Boolean = line.startsWith("|") && line.count { it == '|' } >= 2

private fun parseTable(lines: List<String>): MdBlock.Table {
    val rows = lines.map { line ->
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
    }
    // Row 2 of a GFM table is the |---|---| alignment rule, which carries no content.
    val body = rows.drop(1).filterNot { row -> row.all { it.isBlank() || it.all { c -> c == '-' || c == ':' } } }
    return MdBlock.Table(header = rows.firstOrNull().orEmpty(), rows = body)
}
