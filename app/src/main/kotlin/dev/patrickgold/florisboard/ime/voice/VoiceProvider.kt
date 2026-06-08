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

import androidx.annotation.DrawableRes
import dev.patrickgold.florisboard.R

data class ProviderPreset(
    val id: String,
    val name: String,
    val tagline: String,
    @DrawableRes val iconRes: Int,
    val baseUrl: String,
    val defaultModel: String,
    val isStt: Boolean = true,
    val isLlm: Boolean = false,
    val requiresApiKey: Boolean = true,
    val docsUrl: String? = null,
)

val STT_PRESETS = listOf(
    ProviderPreset(
        id = "openai_stt",
        name = "OpenAI",
        tagline = "Whisper · paid · world-class accuracy",
        iconRes = R.drawable.ic_provider_openai,
        baseUrl = "https://api.openai.com",
        defaultModel = "whisper-1",
        docsUrl = "https://platform.openai.com/docs/guides/speech-to-text",
    ),
    ProviderPreset(
        id = "groq_stt",
        name = "Groq",
        tagline = "Distil-Whisper · free tier · ultra-fast",
        iconRes = R.drawable.ic_provider_groq,
        baseUrl = "https://api.groq.com/openai",
        defaultModel = "whisper-large-v3-turbo",
        docsUrl = "https://console.groq.com/docs/speech-text",
    ),
    ProviderPreset(
        id = "mistral_stt",
        name = "Mistral",
        tagline = "Whisper · EU-based · paid",
        iconRes = R.drawable.ic_provider_mistral,
        baseUrl = "https://api.mistral.ai",
        defaultModel = "whisper-large-v3",
        docsUrl = "https://docs.mistral.ai",
    ),
    ProviderPreset(
        id = "together_stt",
        name = "Together AI",
        tagline = "Open-source models · cheap inference",
        iconRes = R.drawable.ic_provider_together,
        baseUrl = "https://api.together.xyz",
        defaultModel = "whisper-large-v3",
        docsUrl = "https://docs.together.ai",
    ),
    ProviderPreset(
        id = "fal_stt",
        name = "fal.ai",
        tagline = "Serverless AI · pay-per-use",
        iconRes = R.drawable.ic_provider_fal,
        baseUrl = "https://fal.ai",
        defaultModel = "fal-ai/whisper",
        docsUrl = "https://fal.ai/models",
    ),
    ProviderPreset(
        id = "azure_stt",
        name = "Azure OpenAI",
        tagline = "Enterprise · GDPR-ready · private deployment",
        iconRes = R.drawable.ic_provider_azure,
        baseUrl = "https://YOUR-RESOURCE.openai.azure.com",
        defaultModel = "whisper",
        docsUrl = "https://learn.microsoft.com/azure/ai-services/openai",
    ),
)

val LLM_PRESETS = listOf(
    ProviderPreset(
        id = "openai_llm",
        name = "OpenAI",
        tagline = "GPT-4o · best quality · paid",
        iconRes = R.drawable.ic_provider_openai,
        baseUrl = "https://api.openai.com",
        defaultModel = "gpt-4o-mini",
        isStt = false,
        isLlm = true,
        docsUrl = "https://platform.openai.com/docs",
    ),
    ProviderPreset(
        id = "anthropic_llm",
        name = "Anthropic",
        tagline = "Claude · fast + smart · paid",
        iconRes = R.drawable.ic_provider_anthropic,
        baseUrl = "https://api.anthropic.com",
        defaultModel = "claude-haiku-4-5",
        isStt = false,
        isLlm = true,
        docsUrl = "https://docs.anthropic.com",
    ),
    ProviderPreset(
        id = "groq_llm",
        name = "Groq",
        tagline = "Llama 3 · free tier · lightning fast",
        iconRes = R.drawable.ic_provider_groq,
        baseUrl = "https://api.groq.com/openai",
        defaultModel = "llama-3.1-8b-instant",
        isStt = false,
        isLlm = true,
        docsUrl = "https://console.groq.com/docs",
    ),
    ProviderPreset(
        id = "gemini_llm",
        name = "Google Gemini",
        tagline = "Gemini Flash · generous free tier",
        iconRes = R.drawable.ic_provider_gemini,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.0-flash",
        isStt = false,
        isLlm = true,
        docsUrl = "https://ai.google.dev",
    ),
    ProviderPreset(
        id = "mistral_llm",
        name = "Mistral AI",
        tagline = "Mistral · EU-based · paid",
        iconRes = R.drawable.ic_provider_mistral,
        baseUrl = "https://api.mistral.ai",
        defaultModel = "mistral-small-latest",
        isStt = false,
        isLlm = true,
        docsUrl = "https://docs.mistral.ai",
    ),
    ProviderPreset(
        id = "together_llm",
        name = "Together AI",
        tagline = "Open models · cheap inference",
        iconRes = R.drawable.ic_provider_together,
        baseUrl = "https://api.together.xyz",
        defaultModel = "meta-llama/Llama-3.2-3B-Instruct-Turbo",
        isStt = false,
        isLlm = true,
        docsUrl = "https://docs.together.ai",
    ),
    ProviderPreset(
        id = "openrouter_llm",
        name = "OpenRouter",
        tagline = "100+ models · one API key",
        iconRes = R.drawable.ic_provider_openrouter,
        baseUrl = "https://openrouter.ai/api",
        defaultModel = "meta-llama/llama-3.1-8b-instruct:free",
        isStt = false,
        isLlm = true,
        docsUrl = "https://openrouter.ai/docs",
    ),
    ProviderPreset(
        id = "deepseek_llm",
        name = "DeepSeek",
        tagline = "DeepSeek V3 · cheap · strong reasoning",
        iconRes = R.drawable.ic_provider_deepseek,
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-chat",
        isStt = false,
        isLlm = true,
        docsUrl = "https://platform.deepseek.com",
    ),
    ProviderPreset(
        id = "ollama_llm",
        name = "Ollama (local)",
        tagline = "Any model · fully offline · private",
        iconRes = R.drawable.ic_provider_ollama,
        baseUrl = "http://localhost:11434/v1",
        defaultModel = "llama3.2",
        isStt = false,
        isLlm = true,
        requiresApiKey = false,
        docsUrl = "https://ollama.com",
    ),
)

@Deprecated("Kept for backward compatibility during migration")
enum class VoiceProvider {
    OPENAI,
    GROQ,
    CUSTOM,
}
