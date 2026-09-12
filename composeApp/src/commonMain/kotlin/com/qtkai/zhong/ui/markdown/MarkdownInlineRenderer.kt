package com.qtkai.zhong.ui.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

@Composable
internal fun List<InlineNode>.toAnnotatedString(
    onLinkClick: ((String) -> Unit)? = null,
): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString { appendInlines(this@toAnnotatedString, colors, onLinkClick) }
}

private fun AnnotatedString.Builder.appendInlines(
    nodes: List<InlineNode>,
    colors: ColorScheme,
    onLinkClick: ((String) -> Unit)?,
) {
    for (n in nodes) appendInline(n, colors, onLinkClick)
}

private fun AnnotatedString.Builder.appendInline(
    node: InlineNode,
    colors: ColorScheme,
    onLinkClick: ((String) -> Unit)?,
) {
    when (node) {
        is Text -> append(node.value)

        is Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendInlines(node.children, colors, onLinkClick)
        }

        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            appendInlines(node.children, colors, onLinkClick)
        }

        is Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            appendInlines(node.children, colors, onLinkClick)
        }

        is InlineCode -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = colors.surfaceVariant,
            ),
        ) {
            append(node.code)
        }

        is Link -> {
            val linkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            )
            if (onLinkClick != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = node.href,
                        styles = linkStyles,
                        linkInteractionListener = { onLinkClick(node.href) },
                    ),
                ) {
                    appendInlines(node.children, colors, onLinkClick)
                }
            } else {
                withLink(
                    LinkAnnotation.Url(url = node.href, styles = linkStyles),
                ) {
                    appendInlines(node.children, colors, onLinkClick)
                }
            }
        }

        is Image -> append(node.alt)

        LineBreak -> append('\n')

        is InlineMath -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            // Fallback path: if math reaches the AnnotatedString builder it means the caller
            // didn't use [InlineContent]. Emit the raw LaTeX so nothing is lost.
            append(node.latex)
        }
    }
}
