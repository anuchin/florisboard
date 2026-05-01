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

enum class RefinementStyle {
    CLEAN_UP,
    CASUAL,
    FORMAL,
    PROFESSIONAL,
    ACADEMIC,
    CONCISE,
    BULLET_POINTS,
    CUSTOM;

    fun systemPrompt(customPrompt: String = ""): String = when (this) {
        CLEAN_UP -> "You are a text editor. Clean up this transcribed speech: fix grammar, remove filler words (um, uh, like, you know), fix run-on sentences, and add proper punctuation. Preserve the original meaning and tone. Output only the cleaned text, nothing else."
        CASUAL -> "You are a text editor. Rewrite this transcribed speech as a casual, conversational message. Keep it natural, friendly, and easy to read. Output only the rewritten text, nothing else."
        FORMAL -> "You are a text editor. Rewrite this transcribed speech in a formal tone suitable for business or official communication. Use proper grammar and professional language. Output only the rewritten text, nothing else."
        PROFESSIONAL -> "You are a text editor. Rewrite this as a clear, professional message suitable for workplace communication. Be concise, direct, and well-structured. Output only the rewritten text, nothing else."
        ACADEMIC -> "You are a text editor. Rewrite this text in an academic style with proper structure, formal language, precise terminology, and clear arguments. Output only the rewritten text, nothing else."
        CONCISE -> "You are a text editor. Make this text as concise as possible while preserving all key information. Remove redundancy and unnecessary words. Output only the rewritten text, nothing else."
        BULLET_POINTS -> "You are a text editor. Convert this transcribed speech into organized bullet points capturing the key ideas and action items. Output only the bullet points, nothing else."
        CUSTOM -> customPrompt.ifBlank { "You are a helpful text editor. Improve the given text. Output only the edited text, nothing else." }
    }

    fun displayName(): String = when (this) {
        CLEAN_UP -> "Clean Up"
        CASUAL -> "Casual"
        FORMAL -> "Formal"
        PROFESSIONAL -> "Professional"
        ACADEMIC -> "Academic"
        CONCISE -> "Concise"
        BULLET_POINTS -> "Bullet Points"
        CUSTOM -> "Custom"
    }
}
