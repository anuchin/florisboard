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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WhisperApiClient(
    private val baseUrl: String,
    private val apiKey: String,
) : TranscriptionEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audioBytes: ByteArray, config: TranscriptionConfig): TranscriptionResult =
        withContext(Dispatchers.IO) {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", audioBytes.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", config.model)
                .apply {
                    if (config.language.isNotEmpty()) {
                        addFormDataPart("language", config.language)
                    }
                }
                .build()

            val request = Request.Builder()
                .url("$baseUrl/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from API")

            if (!response.isSuccessful) {
                throw Exception("API error ${response.code}: $responseBody")
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            TranscriptionResult(
                text = jsonResponse["text"]?.jsonPrimitive?.content ?: "",
            )
        }

    override suspend fun validateApiKey(): ValidationResult =
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
                    ValidationResult(isSuccess = false, errorMessage = "API returned ${response.code}: $body")
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
