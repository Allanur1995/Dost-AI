package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.CodeTerminalBg
import com.example.ui.theme.CodeTerminalBorder
import com.example.ui.theme.CodeTerminalHeader
import com.example.ui.theme.CodeTerminalText
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.MonoFontFamily
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.delay
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.MarkdownTokenTypes

@Composable
fun MarkdownMessageContent(
    markdown: String,
    textColor: Color,
    copyCodeLabel: String,
    copiedLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = markdownColor(
        text = textColor,
        codeText = MaterialTheme.colorScheme.primary,
        inlineCodeText = MaterialTheme.colorScheme.primary,
        linkText = MaterialTheme.colorScheme.primary,
        codeBackground = CodeTerminalBg,
        inlineCodeBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        dividerColor = MaterialTheme.colorScheme.outlineVariant
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.titleLarge.copy(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold
        ),
        h2 = MaterialTheme.typography.titleMedium.copy(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold
        ),
        h3 = MaterialTheme.typography.titleSmall.copy(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.labelLarge,
        h6 = MaterialTheme.typography.labelMedium,
        text = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFontFamily),
        paragraph = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFontFamily),
        ordered = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFontFamily),
        bullet = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFontFamily),
        list = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFontFamily),
        quote = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = BodyFontFamily,
            fontStyle = FontStyle.Italic
        ),
        code = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MonoFontFamily,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        ),
        inlineCode = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = MonoFontFamily,
            fontSize = 13.5.sp
        )
    )

    val components = markdownComponents(
        codeFence = { model ->
            val language = extractFenceLanguage(model.node, model.content)
            val codeText = extractFenceCode(model.node, model.content)
            CodeBlockCard(
                language = language,
                code = codeText,
                copyCodeLabel = copyCodeLabel,
                copiedLabel = copiedLabel
            )
        },
        codeBlock = { model ->
            val codeText = model.node.getTextInNode(model.content).toString().trimEnd()
            CodeBlockCard(
                language = "code",
                code = codeText,
                copyCodeLabel = copyCodeLabel,
                copiedLabel = copiedLabel
            )
        }
    )

    SelectionContainer(modifier = modifier) {
        Markdown(
            content = markdown,
            colors = colors,
            typography = typography,
            components = components,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun extractFenceLanguage(node: ASTNode, fullContent: String): String {
    val langNode = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
    return langNode?.getTextInNode(fullContent)?.toString()?.trim().orEmpty()
}

private fun extractFenceCode(node: ASTNode, fullContent: String): String {
    val raw = node.getTextInNode(fullContent).toString()
    val lines = raw.lines()
    if (lines.isEmpty()) return raw
    val startIdx = if (lines.first().trimStart().startsWith("```") ||
        lines.first().trimStart().startsWith("~~~")
    ) 1 else 0
    val endIdx = if (lines.size > startIdx &&
        (lines.last().trim() == "```" || lines.last().trim() == "~~~")
    ) lines.size - 1 else lines.size
    return lines.subList(startIdx, endIdx).joinToString("\n").trimEnd()
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    copyCodeLabel: String,
    copiedLabel: String
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CodeTerminalBg)
            .border(1.dp, CodeTerminalBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeTerminalHeader)
                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("copy_code_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_code),
                    tint = if (copied) MaterialTheme.colorScheme.primary else CodeTerminalText.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                Text(
                    text = if (copied) copiedLabel else copyCodeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) MaterialTheme.colorScheme.primary else CodeTerminalText.copy(alpha = 0.85f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                color = CodeTerminalText
            )
        }
    }
}
