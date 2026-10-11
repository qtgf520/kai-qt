package com.qtkai.zhong.ui.chat.composables

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tools_count
import kai.composeapp.generated.resources.waiting_brewing
import kai.composeapp.generated.resources.waiting_content_description
import kai.composeapp.generated.resources.waiting_thinking
import kai.composeapp.generated.resources.waiting_working
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.compose.resources.stringResource

/**
 * Best-effort pretty summary of a tool result: if it's the common
 * {"success":true,"stdout":...,"stderr":...,"error":...} shape, show a
 * readable one-liner; otherwise return the raw text truncated for preview.
 */
internal fun summarizeToolResult(raw: String, maxLen: Int = 160): String {
    if (raw.isBlank()) return raw
    val json = try { Json.parseToJsonElement(raw) } catch (_: Exception) { null }
    if (json is JsonObject) {
        val success = json["success"]?.jsonPrimitive?.content?.takeIf { it != "null" }
        val stdout = json["stdout"]?.jsonPrimitive?.content
        val stderr = json["stderr"]?.jsonPrimitive?.content
        val error = json["error"]?.jsonPrimitive?.content
        val timedOut = json["timed_out"]?.jsonPrimitive?.content
        return when {
            error != null && error != "null" -> "失败: $error"
            stdout != null && stdout != "null" && stdout.isNotBlank() ->
                stdout.trim().lines().firstOrNull()?.take(maxLen) ?: "(空输出)"
            stderr != null && stderr != "null" && stderr.isNotBlank() -> "stderr: $stderr".take(maxLen)
            success != null && success != "null" -> if (success == "true") "✅ 完成" else "❌ 失败"
            timedOut != null && timedOut == "true" -> "⏱ 超时"
            else -> raw.take(maxLen)
        }
    }
    return raw.take(maxLen)
}

@Composable
internal fun toolSummaryText(
    executingTools: ImmutableList<Pair<String, String>>,
): String? = when {
    executingTools.isEmpty() -> null
    executingTools.size == 1 -> executingTools.first().second
    else -> stringResource(Res.string.tools_count, executingTools.size)
}

/**
 * Advanced tool-call status card.
 *
 * Shows each in-flight tool with a status icon, the tool name, a pulsing
 * "working" indicator, and an expandable region that reveals the raw arguments.
 * Completed calls show a check; failed calls show an error chip.
 */
@Composable
internal fun WaitingResponseRow(
    executingTools: ImmutableList<Pair<String, String>>,
    isStatusOnly: Boolean = false,
    statusText: String? = null,
) {
    val summary = statusText ?: toolSummaryText(executingTools)
    val effectiveStatusOnly = isStatusOnly || statusText != null
    val waitingCd = stringResource(Res.string.waiting_content_description)
    if (!effectiveStatusOnly && executingTools.isNotEmpty()) {
        // In-flight tool chips: one quiet row per running tool — icon + name only,
        // no loud card. Keeps the stream light while work is happening.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            executingTools.forEach { (_, toolName) ->
                ToolCallChip(
                    toolName = toolName,
                    isRunning = true,
                )
            }
        }
    } else {
        // Minimal status line: pulsing dot + text, no background box at all.
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clipToBounds()
                .semantics { contentDescription = waitingCd },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingStatusIndicator(
                toolSummary = summary,
                isStatusOnly = effectiveStatusOnly,
                statusText = statusText,
                dotSize = 12.dp,
                dotColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
/** A single in-flight or completed tool call chip with icon + name + status. */
@Composable
private fun ToolCallChip(
    toolName: String,
    isRunning: Boolean,
    isError: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isError -> Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                isRunning -> {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
                    )
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
                else -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = toolName,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Pipeline-style tool call shown inline in the chat — aligned with the reference
 * visual language: ONE compact row per tool (leading icon + name + one-line
 * summary), never a loud card. Completed calls show a small green check,
 * running calls a pulsing dot, failed calls a red error tint. Tapping expands
 * the full arguments + result inline.
 */
@Composable
internal fun ToolPipelineMessage(
    toolName: String,
    status: String,
    arguments: String? = null,
    result: String? = null,
) {
    val isRunning = status == "running"
    val isError = status == "error"
    var expanded by remember { mutableStateOf(false) }
    val isTerminalLike = toolName.contains("shell", ignoreCase = true) ||
        toolName.contains("terminal", ignoreCase = true) ||
        toolName.contains("command", ignoreCase = true)
    val accent = MaterialTheme.colorScheme.primary
    val doneColor = Color(0xFF4CAF50)
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    // Collapsed preview text: args while running, summarized result when done.
    val preview = when {
        isRunning && !arguments.isNullOrBlank() -> arguments
        !isRunning && !result.isNullOrBlank() -> summarizeToolResult(result)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading status glyph — small, quiet, informative.
            when {
                isRunning -> {
                    val dotTrans = rememberInfiniteTransition()
                    val alpha by dotTrans.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .background(accent, CircleShape),
                    )
                }
                isError -> Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                else -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = doneColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Tool name — accent-colored, semi-bold, single line.
            Text(
                text = toolName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = if (isTerminalLike) mono else MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isRunning) accent else if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // One-line summary / preview.
            if (!expanded && preview != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (isTerminalLike) mono else MaterialTheme.typography.bodySmall.fontFamily,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            // Expand affordance — only when there is detail to reveal.
            if (arguments != null || result != null) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 22.dp, bottom = 6.dp)) {
                if (!arguments.isNullOrBlank()) {
                    Text(
                        text = if (isTerminalLike) "$ " + arguments else "参数: $arguments",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
                        color = if (isTerminalLike) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }
                if (!result.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = summarizeToolResult(result, 4000),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isRunning) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "执行中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
            }
        }
    }
}
@Composable
internal fun PulsingStatusIndicator(
    toolSummary: String?,
    dotSize: Dp,
    dotColor: Color,
    textColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    isStatusOnly: Boolean = false,
    statusText: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        if (isStatusOnly && (toolSummary != null || statusText != null)) {
            Text(
                text = statusText ?: toolSummary ?: "",
                color = textColor,
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            // Static "Thinking…" label while waiting — the real thinking content is
            // streamed into the chat as reasoning bubbles above this row, so a fixed
            // label (no animated word rotation) reads calmer and keeps the user's eye
            // on the progressively revealed reasoning text.
            Text(
                text = stringResource(Res.string.waiting_thinking),
                color = textColor,
                style = textStyle,
            )
            if (toolSummary != null) {
                Text(
                    text = " · $toolSummary",
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}