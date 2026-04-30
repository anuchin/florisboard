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

package dev.patrickgold.florisboard.app.settings.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.voice.ValidationResult
import dev.patrickgold.florisboard.ime.voice.VoiceProvider
import dev.patrickgold.florisboard.ime.voice.WhisperApiClient
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.launch

private val OPENAI_MODELS = listOf("whisper-1")
private val GROQ_MODELS = listOf("whisper-large-v3", "distil-whisper-large-v3-en", "whisper-large-v3-turbo")

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun VoiceScreen() = FlorisScreen {
    title = "Voice Input"
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val provider by prefs.voice.provider.collectAsState()
    val model by prefs.voice.model.collectAsState()
    val language by prefs.voice.language.collectAsState()
    val customEndpointUrl by prefs.voice.customEndpointUrl.collectAsState()

    var showProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }

    val currentApiKeyPref = when (provider) {
        VoiceProvider.OPENAI -> prefs.voice.openaiApiKey
        VoiceProvider.GROQ -> prefs.voice.groqApiKey
        VoiceProvider.CUSTOM -> prefs.voice.customApiKey
    }
    val currentApiKey by currentApiKeyPref.collectAsState()

    content {
        PreferenceGroup(title = "Provider") {
            Preference(
                title = "Speech-to-Text Provider",
                summary = when (provider) {
                    VoiceProvider.OPENAI -> "OpenAI"
                    VoiceProvider.GROQ -> "Groq"
                    VoiceProvider.CUSTOM -> "Custom"
                },
                onClick = { showProviderDialog = true },
            )
        }

        PreferenceGroup(title = "API Configuration") {
            Preference(
                title = when (provider) {
                    VoiceProvider.OPENAI -> "OpenAI API Key"
                    VoiceProvider.GROQ -> "Groq API Key"
                    VoiceProvider.CUSTOM -> "Custom API Key"
                },
                summary = if (currentApiKey.isNotBlank()) {
                    "${currentApiKey.take(4)}${"*".repeat(12)}"
                } else {
                    "Not set"
                },
                onClick = { showApiKeyDialog = true },
            )

            Preference(
                title = "Custom Endpoint URL",
                summary = customEndpointUrl.ifBlank { "Not set" },
                onClick = { showEndpointDialog = true },
                visibleIf = { prefs.voice.provider isEqualTo VoiceProvider.CUSTOM },
            )

            Preference(
                title = "Validate API Key",
                summary = when {
                    isValidating -> "Checking..."
                    validationResult?.isSuccess == true -> "Connection successful"
                    validationResult?.isSuccess == false -> validationResult?.errorMessage
                    else -> "Test the current API key and endpoint"
                },
                onClick = {
                    isValidating = true
                    validationResult = null
                    scope.launch {
                        val client = buildClientFromPrefs(prefs)
                        val result = client.validateApiKey()
                        validationResult = result
                        isValidating = false
                    }
                },
                enabledIf = { currentApiKeyPref isNotEqualTo "" },
            )
        }

        PreferenceGroup(title = "Model") {
            Preference(
                title = "Model",
                summary = model.ifBlank { "Not set" },
                onClick = { showModelDialog = true },
            )
        }

        PreferenceGroup(title = "Transcription Settings") {
            Preference(
                title = "Language",
                summary = language.ifBlank { "Auto-detect" },
                onClick = { showLanguageDialog = true },
            )

            SwitchPreference(
                prefs.voice.autoCommit,
                title = "Auto-commit transcription",
                summary = "Automatically insert transcribed text into the input field",
            )
        }
    }

    // Provider selection dialog
    if (showProviderDialog) {
        JetPrefAlertDialog(
            title = "Select Provider",
            confirmLabel = "Cancel",
            onConfirm = { showProviderDialog = false },
            onDismiss = { showProviderDialog = false },
        ) {
            Column {
                VoiceProvider.entries.forEach { entry ->
                    Preference(
                        title = when (entry) {
                            VoiceProvider.OPENAI -> "OpenAI"
                            VoiceProvider.GROQ -> "Groq"
                            VoiceProvider.CUSTOM -> "Custom"
                        },
                        summary = when (entry) {
                            VoiceProvider.OPENAI -> "Uses the official OpenAI Whisper API"
                            VoiceProvider.GROQ -> "Uses Groq's hosted Whisper models"
                            VoiceProvider.CUSTOM -> "Provide your own OpenAI-compatible endpoint"
                        },
                        onClick = {
                            prefs.voice.provider.set(entry)
                            showProviderDialog = false
                        },
                    )
                }
            }
        }
    }

    // API Key dialog
    if (showApiKeyDialog) {
        var apiKey by remember { mutableStateOf(currentApiKey) }
        JetPrefAlertDialog(
            title = when (provider) {
                VoiceProvider.OPENAI -> "OpenAI API Key"
                VoiceProvider.GROQ -> "Groq API Key"
                VoiceProvider.CUSTOM -> "Custom API Key"
            },
            confirmLabel = "Save",
            onConfirm = {
                currentApiKeyPref.set(apiKey.trim())
                showApiKeyDialog = false
                validationResult = null
            },
            dismissLabel = "Cancel",
            onDismiss = { showApiKeyDialog = false },
        ) {
            JetPrefTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
            )
        }
    }

    // Custom Endpoint dialog
    if (showEndpointDialog) {
        var endpointUrl by remember { mutableStateOf(customEndpointUrl) }
        JetPrefAlertDialog(
            title = "Custom Endpoint URL",
            confirmLabel = "Save",
            onConfirm = {
                prefs.voice.customEndpointUrl.set(endpointUrl.trimEnd('/'))
                showEndpointDialog = false
                validationResult = null
            },
            dismissLabel = "Cancel",
            onDismiss = { showEndpointDialog = false },
        ) {
            Column {
                Text(
                    "Enter the base URL for the OpenAI-compatible API.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                JetPrefTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Example: https://api.example.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Model selection dialog
    if (showModelDialog) {
        var selectedModel by remember { mutableStateOf(model) }
        val suggestedModels = when (provider) {
            VoiceProvider.OPENAI -> OPENAI_MODELS
            VoiceProvider.GROQ -> GROQ_MODELS
            VoiceProvider.CUSTOM -> OPENAI_MODELS + GROQ_MODELS
        }
        JetPrefAlertDialog(
            title = "Select Model",
            confirmLabel = "Save",
            onConfirm = {
                prefs.voice.model.set(selectedModel)
                if (provider == VoiceProvider.CUSTOM) {
                    prefs.voice.customModel.set(selectedModel)
                }
                showModelDialog = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showModelDialog = false },
        ) {
            Column {
                suggestedModels.forEach { suggested ->
                    Preference(
                        title = suggested,
                        onClick = { selectedModel = suggested },
                    )
                }
                Text(
                    "Or enter a custom model name:",
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                JetPrefTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                )
            }
        }
    }

    // Language dialog
    if (showLanguageDialog) {
        var languageValue by remember { mutableStateOf(language) }
        JetPrefAlertDialog(
            title = "Language",
            confirmLabel = "Save",
            onConfirm = {
                prefs.voice.language.set(languageValue.trim())
                showLanguageDialog = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showLanguageDialog = false },
        ) {
            Column {
                Text(
                    "Enter an ISO 639-1 language code (e.g., en, es, fr, de, ja).",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Leave empty for auto-detection.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                JetPrefTextField(
                    value = languageValue,
                    onValueChange = { languageValue = it },
                )
            }
        }
    }
}

private fun buildClientFromPrefs(prefs: FlorisPreferenceModel): WhisperApiClient {
    val provider = prefs.voice.provider.get()
    return when (provider) {
        VoiceProvider.OPENAI -> WhisperApiClient(
            baseUrl = "https://api.openai.com",
            apiKey = prefs.voice.openaiApiKey.get(),
        )
        VoiceProvider.GROQ -> WhisperApiClient(
            baseUrl = "https://api.groq.com/openai",
            apiKey = prefs.voice.groqApiKey.get(),
        )
        VoiceProvider.CUSTOM -> WhisperApiClient(
            baseUrl = prefs.voice.customEndpointUrl.get().trimEnd('/'),
            apiKey = prefs.voice.customApiKey.get(),
        )
    }
}
