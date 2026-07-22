/*
 * Copyright (C) 2025 The VoxKB Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voxkb.ime.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}


class LlmApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val presetId: String = "",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val protocol: LlmProtocol = when (presetId) {
        "anthropic_llm" -> LlmProtocol.ANTHROPIC
        "gemini_llm" -> LlmProtocol.GEMINI
        else -> LlmProtocol.OPENAI_COMPATIBLE
    }

    suspend fun refineText(text: String, systemPrompt: String): String {
        val request = when (protocol) {
            LlmProtocol.OPENAI_COMPATIBLE -> openAiRequest(text, systemPrompt)
            LlmProtocol.ANTHROPIC -> anthropicRequest(text, systemPrompt)
            LlmProtocol.GEMINI -> geminiRequest(text, systemPrompt)
        }

        val response = client.newCall(request).awaitResponse()
        return response.use {
            val responseBody = it.body?.string()
                ?: throw Exception("Empty response from LLM API")

            if (!it.isSuccessful) {
                throw Exception("LLM API error ${it.code}: $responseBody")
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val content = when (protocol) {
                LlmProtocol.OPENAI_COMPATIBLE -> jsonResponse["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
                LlmProtocol.ANTHROPIC -> jsonResponse["content"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                LlmProtocol.GEMINI -> jsonResponse["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
            } ?: throw Exception("No content in LLM response")

            content.trim()
        }
    }

    suspend fun validateApiKey(): ValidationResult {
        val request = modelsRequest()

        return try {
            val response = client.newCall(request).awaitResponse()
            response.use {
                if (it.isSuccessful) {
                    ValidationResult(isSuccess = true)
                } else {
                    val body = it.body?.string() ?: "Unknown error"
                    ValidationResult(isSuccess = false, errorMessage = "LLM API returned ${it.code}: $body")
                }
            }
        } catch (e: Exception) {
            ValidationResult(isSuccess = false, errorMessage = e.message ?: "Connection failed")
        }
    }

    suspend fun fetchModels(): ModelsResult {
        val request = modelsRequest()

        return try {
            val response = client.newCall(request).awaitResponse()
            response.use {
                val responseBody = it.body?.string()
                    ?: return@use ModelsResult(emptyList(), "Empty response")

                if (!it.isSuccessful) {
                    return@use ModelsResult(emptyList(), "API returned ${it.code}")
                }

                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
                val modelIds = when (protocol) {
                    LlmProtocol.GEMINI -> jsonResponse["models"]?.jsonArray?.mapNotNull { element ->
                        element.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                    }
                    else -> jsonResponse["data"]?.jsonArray?.mapNotNull { element ->
                        element.jsonObject["id"]?.jsonPrimitive?.content
                    }
                }?.sorted() ?: emptyList()

                ModelsResult(modelIds)
            }
        } catch (e: Exception) {
            ModelsResult(emptyList(), e.message ?: "Connection failed")
        }
    }

    private fun openAiRequest(text: String, systemPrompt: String): Request {
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(systemPrompt))
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(text))
                })
            })
            put("temperature", JsonPrimitive(0.3))
        }
        return Request.Builder()
            .url(joinApiUrl(baseUrl, "v1/chat/completions"))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun anthropicRequest(text: String, systemPrompt: String): Request {
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("max_tokens", JsonPrimitive(2048))
            put("temperature", JsonPrimitive(0.3))
            put("system", JsonPrimitive(systemPrompt))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(text))
                })
            })
        }
        return Request.Builder()
            .url(joinApiUrl(baseUrl, "v1/messages"))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun geminiRequest(text: String, systemPrompt: String): Request {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", JsonPrimitive(systemPrompt)) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(text)) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.3))
            })
        }
        val url = joinApiUrl(baseUrl, "models/$model:generateContent").toHttpUrl().newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        return Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun modelsRequest(): Request {
        val builder = Request.Builder()
        return when (protocol) {
            LlmProtocol.OPENAI_COMPATIBLE -> builder
                .url(joinApiUrl(baseUrl, "v1/models"))
                .addHeader("Authorization", "Bearer $apiKey")
            LlmProtocol.ANTHROPIC -> builder
                .url(joinApiUrl(baseUrl, "v1/models"))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
            LlmProtocol.GEMINI -> builder
                .url(joinApiUrl(baseUrl, "models").toHttpUrl().newBuilder().addQueryParameter("key", apiKey).build())
        }.get().build()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private enum class LlmProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
}
