package com.qtkai.zhong.streaming

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * True server-sent-event streaming for OpenAI-compatible chat completions.
 *
 * The normal path waits for the full response, which is why thinking text only
 * appears once the whole turn finishes. This client opens a `stream=true`
 * request and emits reasoning/content deltas as they arrive, so the UI can
 * show the AI "thinking" character by character while it is still working.
 *
 * Only the delta fields we care about are decoded:
 *  - `choices[0].delta.reasoning_content` / `reasoning` -> reasoning text
 *  - `choices[0].delta.content`                       -> answer text
 *  - `choices[0].finish_reason`                       -> turn finished
 *
 * [onReasoningDelta] and [onContentDelta] are invoked on the OkHttp thread;
 * callers are expected to hop to a UI dispatcher before mutating Compose state.
 */
class StreamingChatClient(
    private val baseUrl: String,
    private val apiKey: String?,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // infinite read: SSE stays open
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun stream(
        model: String,
        messages: List<Pair<String, String>>,
        reasoningEffort: String? = null,
        onReasoningDelta: (String) -> Unit = {},
        onContentDelta: (String) -> Unit = {},
        onFinish: (String?) -> Unit = {},
        onError: (String) -> Unit = {},
    ): EventSource {
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put(
                "messages",
                org.json.JSONArray().apply {
                    for ((role, content) in messages) {
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", content)
                        })
                    }
                },
            )
            if (!reasoningEffort.isNullOrBlank()) {
                put("reasoning_effort", reasoningEffort)
            }
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .apply {
                if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
            }
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        return EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    if (data == "[DONE]") {
                        onFinish(null)
                        return
                    }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) return
                        val first = choices.optJSONObject(0) ?: return
                        // Finish reason signals the turn is over.
                        if (first.has("finish_reason")) {
                            onFinish(first.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" })
                        }
                        val delta = first.optJSONObject("delta") ?: return
                        delta.optString("reasoning_content").takeIf { it.isNotEmpty() }?.let(onReasoningDelta)
                        delta.optString("reasoning").takeIf { it.isNotEmpty() }?.let(onReasoningDelta)
                        delta.optString("content").takeIf { it.isNotEmpty() }?.let(onContentDelta)
                    } catch (_: Exception) {
                        // Ignore malformed chunks; keep the stream alive.
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    onError(t?.message ?: "stream failed (HTTP ${response?.code ?: "?"})")
                }

                override fun onClosed(eventSource: EventSource) {
                    onFinish(null)
                }
            },
        )
    }
}