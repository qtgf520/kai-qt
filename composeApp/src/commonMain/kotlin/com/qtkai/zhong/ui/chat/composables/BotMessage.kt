package com.qtkai.zhong.ui.chat.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtkai.zhong.getBackgroundDispatcher
import com.qtkai.zhong.ui.dynamicui.FrozenSubmission
import com.qtkai.zhong.ui.dynamicui.toSpeakableText
import com.qtkai.zhong.ui.handCursor
import com.qtkai.zhong.ui.markdown.MarkdownContent
import com.qtkai.zhong.ui.markdown.parseMarkdown
import com.qtkai.zhong.ui.rememberCopyToClipboard
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.bot_message_copy_content_description
import kai.composeapp.generated.resources.bot_message_flag_content_description
import kai.composeapp.generated.resources.bot_message_header_label
import kai.composeapp.generated.resources.bot_message_regenerate_content_description
import kai.composeapp.generated.resources.bot_message_speech_content_description
import kai.composeapp.generated.resources.bot_message_thinking_expand_content_description
import kai.composeapp.generated.resources.bot_message_thinking_label
import kai.composeapp.generated.resources.chat_cancel_edit_content_description
import kai.composeapp.generated.resources.chat_edit_submission_content_description
import kai.composeapp.generated.resources.ic_close
import kai.composeapp.generated.resources.ic_copy
import kai.composeapp.generated.resources.ic_flag
import kai.composeapp.generated.resources.ic_refresh
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_volume_up
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.stringResource

/**
 * One step in an assistant reply's tool pipeline: a tool invocation (with its
 * arguments) and the result that came back. Rendered between the thinking block
 * and the final answer so a single reply reads as one continuous working
 * session — think → call tools → see results → answer — instead of separate
 * disconnected bubbles.
 *
 * [sourceId] is the originating History id (TOOL / TOOL_EXECUTING entry). The
 * renderer matches on it to drop the standalone row for steps already drawn
 * inside a pipeline — exact id match, never a tool-name match, so two replies
 * that call the same tool (e.g. execute_shell_command twice) can't hide each
 * other's results.
 */
data class ToolStep(
    val toolName: String,
    val arguments: String? = null,
    val result: String? = null,
    val status: String = "done",
    val sourceId: String? = null,
)

@Composable
internal fun BotMessage(
    message: String,
    textToSpeech: TextToSpeechInstance?,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    isInteractive: Boolean = false,
    onUiCallback: ((event: String, data: Map<String, String>) -> Unit)? = null,
    frozen: FrozenSubmission? = null,
    onResubmit: ((event: String, data: Map<String, String>) -> Unit)? = null,
    reasoningSegments: ImmutableList<String> = persistentListOf(),
    // [REQ-5.4] Optional delete action for this message.
    onDelete: (() -> Unit)? = null,
    // When true (latest in-flight assistant), the thinking block writes itself out
    // character-by-character (handwriting feel). Historic answers render in full.
    animateReasoning: Boolean = false,
    // Service/model label shown in the message header (e.g. fallback service name).
    serviceLabel: String? = null,
    // Tool pipeline steps between thinking and answer — renders the reply as one
    // continuous working session (think → call → result → answer).
    toolSteps: ImmutableList<ToolStep> = persistentListOf(),
) {
    // The FINAL ANSWER renders in full — no typewriter. Only the reasoning/thinking
    // block above animates progressively (see ReasoningBlockquote); the answer text
    // appears complete and instant so the message pipeline reads naturally.
    val document = remember(message) { parseMarkdown(message) }
    var isEditing by remember(frozen) { mutableStateOf(false) }
    val effectiveFrozen = if (isEditing && frozen != null) frozen.copy(pressedEvent = null) else frozen
    val effectiveInteractive = if (frozen != null) (onResubmit != null && isEditing) else isInteractive
    val kaiUiCallback: (String, Map<String, String>) -> Unit = if (onResubmit != null) {
        { event, data ->
            isEditing = false
            onResubmit(event, data)
        }
    } else {
        onUiCallback ?: { _, _ -> }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                // No card border/background — plain flowing text, matching the
                // chat style in the reference: messages read as a clean stream
                // with only subtle labels, never as boxed cards.
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // Minimal header: a small quiet label on the left, optional service
            // name on the right. No divider, no chrome — the reference keeps the
            // meta bar feather-light so the content below reads uninterrupted.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.bot_message_header_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
                if (!serviceLabel.isNullOrBlank()) {
                    Text(
                        text = serviceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
            val nonBlankSegments = remember(reasoningSegments) {
                reasoningSegments.filter { it.isNotBlank() }.toImmutableList()
            }
            if (nonBlankSegments.isNotEmpty()) {
                ReasoningBlockquote(
                    segments = nonBlankSegments,
                    animate = animateReasoning,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
            }
            // Tool pipeline: each invocation + result renders inline between the
            // thinking block and the final answer, keeping one reply visually
            // continuous (think → call tools → see results → answer).
            if (toolSteps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = if (nonBlankSegments.isNotEmpty()) 10.dp else 14.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    toolSteps.forEach { step ->
                        ToolPipelineMessage(
                            toolName = step.toolName,
                            status = step.status,
                            arguments = step.arguments,
                            result = step.result,
                        )
                    }
                }
            }
            if (message.isNotEmpty()) {
                // The answer continues the same stepped timeline: keep the left
                // gutter aligned with the reasoning block so the whole response
                // reads as one cascade (reason → tools → final answer).
                val answerTopPadding = if (nonBlankSegments.isNotEmpty()) 6.dp else 16.dp
                SelectionContainer {
                    MarkdownContent(
                        document = document,
                        isInteractive = effectiveInteractive,
                        onUiCallback = kaiUiCallback,
                        frozen = effectiveFrozen,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = answerTopPadding, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
        if (frozen != null && onResubmit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .handCursor()
                    .clickable { isEditing = !isEditing },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (isEditing) Res.string.chat_cancel_edit_content_description else Res.string.chat_edit_submission_content_description,
                    ),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (message.isEmpty()) return
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton(
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = stringResource(Res.string.bot_message_speech_content_description),
                onClick = {
                    componentScope.launch(getBackgroundDispatcher()) {
                        textToSpeech.stop()
                        if (isSpeaking) {
                            setIsSpeaking(false)
                        } else {
                            setIsSpeaking(true)
                            try {
                                textToSpeech.say(text = message.toSpeakableText())
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) {
                                // Expected interruption - no action needed
                            } catch (e: Exception) {
                                // Handle TTS errors gracefully (service failure, audio issues, etc.)
                            }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        val copyToClipboard = rememberCopyToClipboard()
        SmallIconButton(
            iconResource = Res.drawable.ic_copy,
            contentDescription = stringResource(Res.string.bot_message_copy_content_description),
            onClick = { copyToClipboard(message) },
        )
        run {
            val uriHandler = LocalUriHandler.current
            SmallIconButton(
                iconResource = Res.drawable.ic_flag,
                contentDescription = stringResource(Res.string.bot_message_flag_content_description),
                onClick = {
                    uriHandler.openUri("https://form.jotform.com/250014908169355")
                },
            )
        }
        if (onRegenerate != null) {
            SmallIconButton(
                iconResource = Res.drawable.ic_refresh,
                contentDescription = stringResource(Res.string.bot_message_regenerate_content_description),
                onClick = onRegenerate,
            )
        }
        if (onDelete != null) {
            SmallIconButton(
                iconResource = Res.drawable.ic_close,
                contentDescription = "删除此消息",
                onClick = onDelete,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ReasoningBlockquote(
    segments: ImmutableList<String>,
    modifier: Modifier = Modifier,
    // When true (the latest in-flight assistant message), the thinking text is
    // revealed character-by-character like handwriting — a natural "the AI is
    // thinking right now" feel. Historic messages render in full instantly.
    animate: Boolean = false,
) {
    // Only the in-flight (animate) thinking block auto-expands so the user can watch
    // it being written. Historic reasoning stays collapsed by default — the user
    // taps to expand if they want to read it. Empty/placeholder segments stay
    // collapsed too.
    var expanded by remember(segments.isEmpty(), animate) {
        mutableStateOf(animate && segments.isNotEmpty())
    }
    // Join all thinking segments into one stream. When `animate`, reveal it
    // character-by-character (handwriting feel); otherwise show everything at once.
    val fullText = remember(segments) { segments.joinToString("\n\n") }
    // Key on `animate` only (NOT fullText): when the thinking text grows mid-animation,
    // we must keep the current progress and continue writing — keying on fullText would
    // reset visibleChars to 0 and re-run the effect, which is the bug that left the
    // thinking block blank on streaming updates.
    var visibleChars by remember(animate) { mutableStateOf(if (animate) 0 else fullText.length) }
    LaunchedEffect(fullText, animate) {
        if (!animate) {
            visibleChars = fullText.length
            return@LaunchedEffect
        }
        // Handwriting pace: ~1 char every 18ms (≈55 chars/sec) — slow enough to
        // feel like someone writing, fast enough not to annoy. Continues from the
        // current progress each time the effect restarts (thinking text grew).
        while (visibleChars < fullText.length) {
            visibleChars = (visibleChars + 1).coerceAtMost(fullText.length)
            delay(18)
        }
    }
    val revealedText = if (visibleChars >= fullText.length) fullText else fullText.take(visibleChars)
    // Preview always reflects the MOST RECENT thinking segment so the user gets a
    // visual update each time a new reasoning phase starts, without expanding.
    val preview = remember(segments) {
        segments.lastOrNull()
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }
    // Rotating chevron, matching the reference's CanvasExpandableHeaderRow: a
    // right-pointing arrow that turns 90° when expanded, no card, no divider.
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "thinking-arrow-rotation",
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.bot_message_thinking_expand_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(Res.string.bot_message_thinking_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            )
            if (!expanded && preview.isNotEmpty() && !animate) {
                Text(
                    text = " · $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            // Plain, quiet reasoning text — no vertical dividers, no boxes, no
            // per-segment chrome. Just indented body text that reads naturally.
            Column(
                modifier = Modifier.padding(top = 4.dp, start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val revealedSegments = remember(revealedText) { revealedText.split("\n\n") }
                for (segment in revealedSegments) {
                    SelectionContainer {
                        Text(
                            text = segment,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                // While still writing, show a soft blinking caret so it reads as
                // "in progress", not a truncated message.
                if (animate && visibleChars < fullText.length) {
                    Text(
                        text = "▍",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
