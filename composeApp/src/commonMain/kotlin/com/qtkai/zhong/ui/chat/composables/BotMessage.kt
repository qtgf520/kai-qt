package com.qtkai.zhong.ui.chat.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
            // Header row: "AI 回复" label on the left, service/model on the right —
            // mirrors Operit's Response header bar so the pipeline reads clearly.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.bot_message_header_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                if (!serviceLabel.isNullOrBlank()) {
                    Text(
                        text = serviceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            // Divider under the header — visually separates the meta bar from the
            // thinking / answer body, matching Operit's layered pipeline look.
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
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
            if (message.isNotEmpty()) {
                // When reasoning is shown above, the Thinking row already provides
                // the visual gap to the answer — drop the duplicated top inset.
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.bot_message_thinking_expand_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(Res.string.bot_message_thinking_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!expanded && preview.isNotEmpty()) {
                Text(
                    text = " · $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Render the revealed stream in ONE text block (with the vertical divider
                // styling), split on double newlines so the typewriter still shows the
                // progressive reveal while keeping the original segment layout.
                val revealedSegments = remember(revealedText) { revealedText.split("\n\n") }
                for (segment in revealedSegments) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        VerticalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxHeight(),
                        )
                        SelectionContainer(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = segment,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                // While still writing, show a soft blinking caret so it reads as
                // "in progress", not a truncated message.
                if (animate && visibleChars < fullText.length) {
                    Text(
                        text = "▍",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}
