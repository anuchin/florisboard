/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refineText(text: String, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
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
            }.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from LLM API")

            if (!response.isSuccessful) {
                throw Exception("LLM API error ${response.code}: $responseBody")
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val choices = jsonResponse["choices"]?.jsonArray
            val message = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content
                ?: throw Exception("No content in LLM response")

            content.trim()
        }

    suspend fun validateApiKey(): ValidationResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    ValidationResult(isSuccess = true)
                } else {
                    val body = response.body?.string() ?: "Unknown error"
                    ValidationResult(isSuccess = false, errorMessage = "LLM API returned ${response.code}: $body")
                }
            } catch (e: Exception) {
                ValidationResult(isSuccess = false, errorMessage = e.message ?: "Connection failed")
            }
        }

    suspend fun fetchModels(): ModelsResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext ModelsResult(emptyList(), "Empty response")

                if (!response.isSuccessful) {
                    return@withContext ModelsResult(emptyList(), "API returned ${response.code}")
                }

                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
                val dataArray = jsonResponse["data"]?.jsonArray
                val modelIds = dataArray?.mapNotNull { element ->
                    element.jsonObject["id"]?.jsonPrimitive?.content
                }?.sorted() ?: emptyList()

                ModelsResult(modelIds)
            } catch (e: Exception) {
                ModelsResult(emptyList(), e.message ?: "Connection failed")
            }
        }
}
