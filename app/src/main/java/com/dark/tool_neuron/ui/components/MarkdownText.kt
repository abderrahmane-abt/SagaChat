package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val parsedContent = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier
            .padding(horizontal = rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
    ) {
        parsedContent.forEach { element ->
            when (element) {
                is MarkdownElement.Heading1 -> Heading1(element.text)
                is MarkdownElement.Heading2 -> Heading2(element.text)
                is MarkdownElement.Heading3 -> Heading3(element.text)
                is MarkdownElement.Heading4 -> Heading4(element.text)
                is MarkdownElement.Heading5 -> Heading5(element.text)
                is MarkdownElement.Heading6 -> Heading6(element.text)
                is MarkdownElement.Body -> BodyText(element.text)
                is MarkdownElement.BulletPoint -> BulletPoint(element.text, element.level)
                is MarkdownElement.NumberedPoint -> NumberedPoint(element.text, element.number)
                is MarkdownElement.Quote -> BlockQuote(element.text, element.level)
                is MarkdownElement.CodeBlock -> CodeBlockExpanded(element.code, element.language)
                is MarkdownElement.InlineCode -> InlineCodeText(element.text)
                is MarkdownElement.Table -> TableView(element.headers, element.rows, element.alignments)
                is MarkdownElement.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = rDp(8.dp)),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

private sealed class MarkdownElement {
    data class Heading1(val text: String) : MarkdownElement()
    data class Heading2(val text: String) : MarkdownElement()
    data class Heading3(val text: String) : MarkdownElement()
    data class Heading4(val text: String) : MarkdownElement()
    data class Heading5(val text: String) : MarkdownElement()
    data class Heading6(val text: String) : MarkdownElement()
    data class Body(val text: String) : MarkdownElement()
    data class BulletPoint(val text: String, val level: Int = 0) : MarkdownElement()
    data class NumberedPoint(val text: String, val number: String) : MarkdownElement()
    data class Quote(val text: String, val level: Int = 1) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String) : MarkdownElement()
    data class InlineCode(val text: String) : MarkdownElement()
    data class Table(val headers: List<String>, val rows: List<List<String>>, val alignments: List<Alignment>) : MarkdownElement() {
        enum class Alignment { LEFT, CENTER, RIGHT }
    }
    object Divider : MarkdownElement()
}

private fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), language))
            }
            line.trimStart().startsWith("    ") && line.trim().isNotEmpty() -> {
                val codeLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart().startsWith("    ") || lines[i].isBlank())) {
                    if (lines[i].trim().isNotEmpty()) {
                        codeLines.add(lines[i].removePrefix("    "))
                    }
                    i++
                }
                i--
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), ""))
            }
            line.startsWith("###### ") -> elements.add(MarkdownElement.Heading6(line.removePrefix("###### ")))
            line.startsWith("##### ") -> elements.add(MarkdownElement.Heading5(line.removePrefix("##### ")))
            line.startsWith("#### ") -> elements.add(MarkdownElement.Heading4(line.removePrefix("#### ")))
            line.startsWith("### ") -> elements.add(MarkdownElement.Heading3(line.removePrefix("### ")))
            line.startsWith("## ") -> elements.add(MarkdownElement.Heading2(line.removePrefix("## ")))
            line.startsWith("# ") -> elements.add(MarkdownElement.Heading1(line.removePrefix("# ")))
            line.matches(Regex("^[+\\-*]\\s+.+")) -> {
                val level = line.takeWhile { it == ' ' }.length / 2
                elements.add(MarkdownElement.BulletPoint(line.trimStart().substring(2), level))
            }
            line.matches(Regex("^\\d+\\.\\s+.+")) -> {
                val number = line.substringBefore(".")
                val text = line.substringAfter(". ")
                elements.add(MarkdownElement.NumberedPoint(text, number))
            }
            line.startsWith(">") -> {
                val level = line.takeWhile { it == '>' }.length
                val text = line.substring(level).trim()
                elements.add(MarkdownElement.Quote(text, level))
            }
            line.startsWith("|") && i + 1 < lines.size && lines[i + 1].contains("|") -> {
                val headers = line.split("|").filter { it.isNotBlank() }.map { it.trim() }
                i++
                val alignmentLine = lines[i]
                val alignments = alignmentLine.split("|").filter { it.isNotBlank() }.map {
                    when {
                        it.trim().startsWith(":") && it.trim().endsWith(":") -> MarkdownElement.Table.Alignment.CENTER
                        it.trim().endsWith(":") -> MarkdownElement.Table.Alignment.RIGHT
                        else -> MarkdownElement.Table.Alignment.LEFT
                    }
                }
                i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].startsWith("|")) {
                    rows.add(lines[i].split("|").filter { it.isNotBlank() }.map { it.trim() })
                    i++
                }
                i--
                elements.add(MarkdownElement.Table(headers, rows, alignments))
            }
            line == "---" || line == "___" || line == "***" -> elements.add(MarkdownElement.Divider)
            line.isNotBlank() -> elements.add(MarkdownElement.Body(line))
        }
        i++
    }

    return elements
}

@Composable
private fun Heading1(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(8.dp))
    )
}

@Composable
private fun Heading2(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(7.dp))
    )
}

@Composable
private fun Heading3(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(6.dp))
    )
}

@Composable
private fun Heading4(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(5.dp))
    )
}

@Composable
private fun Heading5(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(4.dp))
    )
}

@Composable
private fun Heading6(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
        modifier = Modifier.padding(vertical = rDp(3.dp))
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
        lineHeight = 22.sp
    )
}

@Composable
private fun processInlineFormatting(text: String) = buildAnnotatedString {
    var i = 0
    val chars = text.toCharArray()

    while (i < chars.size) {
        when {
            i + 1 < chars.size && chars[i] == '*' && chars[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            i + 1 < chars.size && chars[i] == '_' && chars[i + 1] == '_' -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            chars[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }
            chars[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }
            i + 1 < chars.size && chars[i] == '~' && chars[i + 1] == '~' -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            chars[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = maple,
                        background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }
            i + 1 < chars.size && chars[i] == '+' && chars[i + 1] == '+' -> {
                val end = text.indexOf("++", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            i + 1 < chars.size && chars[i] == '=' && chars[i + 1] == '=' -> {
                val end = text.indexOf("==", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            else -> {
                append(chars[i])
                i++
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String, level: Int) {
    Row(
        modifier = Modifier.padding(start = rDp((8 + level * 16).dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Text(
            text = "•",
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(2.dp))
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedPoint(text: String, number: String) {
    Row(
        modifier = Modifier.padding(start = rDp(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Text(
            text = "$number.",
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(2.dp))
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlockQuote(text: String, level: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rDp(((level - 1) * 12).dp))
            .clip(RoundedCornerShape(rDp(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(rDp(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Box(
            modifier = Modifier
                .width(rDp(3.dp))
                .height(rDp(20.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlineCodeText(text: String) {
    Text(
        text = text,
        fontFamily = maple,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(4.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
    )
}

@Composable
private fun TableView(
    headers: List<String>,
    rows: List<List<String>>,
    alignments: List<MarkdownElement.Table.Alignment>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(10.dp)))
            .border(
                width = rDp(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(rDp(10.dp))
            )
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(rDp(12.dp))
        ) {
            headers.forEachIndexed { index, header ->
                Text(
                    text = header,
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = rDp(8.dp))
                )
            }
        }

        rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(12.dp))
            ) {
                row.forEachIndexed { index, cell ->
                    Text(
                        text = cell,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = rDp(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockExpanded(code: String, language: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(10.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(12.dp), vertical = rDp(10.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    onClickListener = {},
                    icon = R.drawable.code,
                    contentDescription = "Code",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (language.isNotEmpty()) {
                    Text(
                        text = language.uppercase(),
                        fontFamily = maple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                ActionButton(
                    onClickListener = { /* Copy code */ },
                    icon = R.drawable.copy,
                    contentDescription = "Copy",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
                ActionToggleButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = !isExpanded },
                    icon = if (isExpanded) R.drawable.up else R.drawable.down,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary.copy(0.12f),
                        checkedContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(rDp(12.dp))
                ) {
                    Text(
                        text = code,
                        fontFamily = maple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}