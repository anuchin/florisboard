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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApiUrlTest : FunSpec({
    test("adds the API version for provider root URLs") {
        joinApiUrl("https://api.openai.com", "v1/models") shouldBe
            "https://api.openai.com/v1/models"
    }

    test("does not duplicate v1 for Ollama and custom endpoints") {
        joinApiUrl("http://localhost:11434/v1/", "/v1/chat/completions") shouldBe
            "http://localhost:11434/v1/chat/completions"
    }

    test("preserves provider-specific prefixes") {
        joinApiUrl("https://api.groq.com/openai", "v1/audio/transcriptions") shouldBe
            "https://api.groq.com/openai/v1/audio/transcriptions"
    }
})
