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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToneStyle {
    CASUAL,
    FORMAL,
    CONCISE,
    FRIENDLY,
    PROFESSIONAL;

    fun displayName(): String = when (this) {
        CASUAL -> "Casual"
        FORMAL -> "Formal"
        CONCISE -> "Concise"
        FRIENDLY -> "Friendly"
        PROFESSIONAL -> "Professional"
    }

    val icon: ImageVector
        get() = when (this) {
            CASUAL -> Icons.Outlined.Chat
            FORMAL -> Icons.Outlined.Description
            CONCISE -> Icons.Outlined.ShortText
            FRIENDLY -> Icons.Outlined.Face
            PROFESSIONAL -> Icons.Outlined.Work
        }

    fun systemPrompt(): String = when (this) {
        CASUAL -> "You are a text editor. Rewrite this transcribed speech as a casual, conversational message. Keep it natural, friendly, and easy to read. Output only the rewritten text, nothing else."
        FORMAL -> "You are a text editor. Rewrite this transcribed speech in a formal tone suitable for business or official communication. Use proper grammar and professional language. Output only the rewritten text, nothing else."
        CONCISE -> "You are a text editor. Make this text as concise as possible while preserving all key information. Remove redundancy and unnecessary words. Output only the rewritten text, nothing else."
        FRIENDLY -> "You are a text editor. Rewrite this transcribed speech in a warm, friendly tone. Make it approachable and positive while keeping the original meaning. Output only the rewritten text, nothing else."
        PROFESSIONAL -> "You are a text editor. Rewrite this as a clear, professional message suitable for workplace communication. Be concise, direct, and well-structured. Output only the rewritten text, nothing else."
    }
}
