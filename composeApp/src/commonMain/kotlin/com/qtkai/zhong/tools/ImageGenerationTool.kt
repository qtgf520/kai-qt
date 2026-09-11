package com.qtkai.zhong.tools

import com.qtkai.zhong.httpClient
import com.qtkai.zhong.network.tools.ParameterSchema
import com.qtkai.zhong.network.tools.Tool
import com.qtkai.zhong.network.tools.ToolInfo
import com.qtkai.zhong.network.tools.ToolSchema
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

private val imageGenJson = Json { ignoreUnknownKeys = true }

/**
 * [REQ-12] Image generation tool.
 *
 * Calls an OpenAI-compatible `/v1/images/generations` endpoint (OpenAI, or any
 * compatible proxy) with the configured API key. Returns the generated image's
 * URL (or base64 when the provider uses b64_json) so the chat can render it.
 */
class ImageGenerationTool(
    private val getApiKey: () -> String,
    private val getBaseUrl: () -> String,
    private val getModel: () -> String,
) : Tool {
    override val schema = ToolSchema(
        name = "generate_image",
        description = "Generate an image from a text prompt using an AI image model (e.g. DALL·E / compatible). " +
            "Returns a URL to the generated image. Use when the user asks to draw, create, or generate a picture.",
        parameters = mapOf(
            "prompt" to ParameterSchema(
                type = "string",
                description = "Detailed description of the image to generate",
                required = true,
            ),
            "size" to ParameterSchema(
                type = "string",
                description = "Image size: 1024x1024, 1792x1024, or 1024x1792 (default 1024x1024)",
                required = false,
            ),
        ),
    )

    override val timeout get() = 120.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val prompt = args["prompt"]?.toString()?.trim()
            ?: return mapOf("success" to false, "error" to "prompt is required")
        val size = args["size"]?.toString()?.takeIf { it.isNotBlank() } ?: "1024x1024"
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return mapOf(
                "success" to false,
                "error" to "Image generation API key is not configured. Add it in Settings → Tools → Image Generation.",
            )
        }
        val baseUrl = getBaseUrl().trimEnd('/').ifEmpty { "https://api.openai.com" }
        val model = getModel().ifEmpty { "dall-e-3" }

        return try {
            val client = httpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                }
            }
            val response = client.post("$baseUrl/v1/images/generations") {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(
                    buildJsonObject {
                        put("model", model)
                        put("prompt", prompt)
                        put("size", size)
                        put("n", 1)
                    },
                )
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                return mapOf("success" to false, "error" to "Image API error ${response.status.value}: ${body.take(300)}")
            }
            val json = imageGenJson.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray?.firstOrNull()
            val url = data?.jsonObject?.get("url")?.jsonPrimitive?.content
            val b64 = data?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
            when {
                !url.isNullOrBlank() -> mapOf("success" to true, "image_url" to url, "prompt" to prompt)
                !b64.isNullOrBlank() -> mapOf("success" to true, "image_b64" to b64, "prompt" to prompt)
                else -> mapOf("success" to false, "error" to "No image data in response: ${body.take(300)}")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Image generation failed: ${e.message?.take(300)}")
        }
    }

    companion object {
        val toolInfo = ToolInfo(
            id = "generate_image",
            name = "Image Generation",
            description = "Generate an image from a text prompt (DALL·E / compatible API)",
            nameRes = null,
            descriptionRes = null,
        )
    }
}