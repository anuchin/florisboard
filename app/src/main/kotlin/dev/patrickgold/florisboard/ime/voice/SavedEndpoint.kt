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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    companion object {
        val Empty = SavedEndpoint("", "", "", "", "whisper-1")
        private val json = Json { ignoreUnknownKeys = true }

        fun serializeList(list: List<SavedEndpoint>): String {
            return json.encodeToString(list)
        }

        fun deserializeList(raw: String): List<SavedEndpoint> {
            if (raw.isBlank() || raw == "[]") return emptyList()
            return try {
                json.decodeFromString<List<SavedEndpoint>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
