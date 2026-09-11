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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
        // Advanced multi-tool card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    executingTools.forEachIndexed { index, (_, toolName) ->
                        if (index > 0) Spacer(Modifier.size(8.dp))
                        ToolCallChip(
                            toolName = toolName,
                            isRunning = true,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingStatusIndicator(
                            toolSummary = null,
                            isStatusOnly = true,
                            statusText = summary,
                            dotSize = 14.dp,
                            dotColor = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    )
                    .padding(12.dp)
                    .semantics { contentDescription = waitingCd },
            ) {
                PulsingStatusIndicator(
                    toolSummary = summary,
                    isStatusOnly = effectiveStatusOnly,
                    statusText = statusText,
                    dotSize = 16.dp,
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
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
 * Pipeline-style tool message shown inline in the chat, like ChatGPT's tool calls.
 * Running tools show a spinner + name; completed tools show a check + name + result.
 * The card is expandable/collapsible: collapsed shows a short preview, expanded shows
 * the FULL arguments and result with automatic line wrapping (no truncation).
 * Running cards auto-expand so streaming output stays visible.
 * Terminal-style tools (shell/terminal) render with a dark monospace console look.
 */
@Composable
internal fun ToolPipelineMessage(
    toolName: String,
    status: String,
    arguments: String? = null,
    result: String? = null,
) {
    val isRunning = status == "running"
    // Running cards start expanded so the user can watch the stream; completed cards
    // start collapsed and expand on tap.
    var expanded by remember { mutableStateOf(isRunning) }
    LaunchedEffect(isRunning) {
        if (isRunning) expanded = true
    }

    val isTerminalLike = toolName.contains("shell", ignoreCase = true) ||
        toolName.contains("terminal", ignoreCase = true) ||
        toolName.contains("command", ignoreCase = true)

    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        isTerminalLike -> Color(0xFF1E1E24) // dark console background
        isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    }
    val fg = when {
        isTerminalLike -> Color(0xFFE0E0E0)
        isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent = if (isTerminalLike) Color(0xFF4EC9B0) else MaterialTheme.colorScheme.primary
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        shape = shape,
        color = bg,
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Header row: status icon + name + expand/collapse chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing)),
                    )
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = if (isTerminalLike) mono else MaterialTheme.typography.labelMedium.fontFamily,
                    ),
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = fg.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Collapsed preview — wraps up to 2 lines so longer commands still readable.
            if (!expanded) {
                val preview = when {
                    isRunning && !arguments.isNullOrBlank() -> arguments
                    !isRunning && !result.isNullOrBlank() -> summarizeToolResult(result)
                    else -> null
                }
                if (!preview.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (isTerminalLike) mono else MaterialTheme.typography.bodySmall.fontFamily,
                        ),
                        color = fg.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Expanded detail — FULL text, automatic wrapping, no truncation.
            if (expanded) {
                if (!arguments.isNullOrBlank()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (isTerminalLike) "$ " + arguments else "参数: $arguments",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
                        color = if (isTerminalLike) Color(0xFF4EC9B0) else fg.copy(alpha = 0.85f),
                    )
                }
                if (!result.isNullOrBlank()) {
                    Spacer(Modifier.size(6.dp))
                    // Full readable result with wrapping — no truncation.
                    Text(
                        text = summarizeToolResult(result, 4000),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
                        color = fg,
                    )
                }
                if (isRunning) {
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                .size(6.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .background(accent, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
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