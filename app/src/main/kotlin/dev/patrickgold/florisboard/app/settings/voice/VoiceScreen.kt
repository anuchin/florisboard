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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.voice.LlmApiClient
import dev.patrickgold.florisboard.ime.voice.RefinementStyle
import dev.patrickgold.florisboard.ime.voice.ModelsResult
import dev.patrickgold.florisboard.ime.voice.SavedEndpoint
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

private data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val defaultWhisperModel: String,
    val defaultLlmModel: String,
)

private val PROVIDER_PRESETS = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com", "whisper-1", "gpt-4o-mini"),
    ProviderPreset("Groq", "https://api.groq.com/openai", "whisper-large-v3", "llama-3.1-8b-instant"),
    ProviderPreset("Mistral", "https://api.mistral.ai", "whisper-1", "mistral-small-latest"),
    ProviderPreset("Together AI", "https://api.together.xyz", "whisper-1", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
    ProviderPreset("Anthropic", "https://api.anthropic.com", "whisper-1", "claude-3-haiku-20240307"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com", "whisper-1", "deepseek-chat"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api", "whisper-1", "openai/gpt-4o-mini"),
)

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
    var showAddEndpointDialog by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showDeleteEndpointConfirm by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showEndpointActionDialog by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showRefinementStyleDialog by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var showAddLlmEndpointDialog by remember { mutableStateOf(false) }
    var editingLlmEndpoint by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showDeleteLlmEndpointConfirm by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showLlmEndpointActionDialog by remember { mutableStateOf<SavedEndpoint?>(null) }

    val savedEndpointsRaw by prefs.voice.savedEndpoints.collectAsState()
    val savedEndpoints = remember(savedEndpointsRaw) {
        SavedEndpoint.deserializeList(savedEndpointsRaw)
    }
    val activeEndpointId by prefs.voice.activeEndpointId.collectAsState()

    val llmSavedEndpointsRaw by prefs.voice.llmSavedEndpoints.collectAsState()
    val llmSavedEndpoints = remember(llmSavedEndpointsRaw) {
        SavedEndpoint.deserializeList(llmSavedEndpointsRaw)
    }
    val llmActiveEndpointId by prefs.voice.llmActiveEndpointId.collectAsState()

    val refinementEnabled by prefs.voice.refinementEnabled.collectAsState()
    val refinementStyle by prefs.voice.refinementStyle.collectAsState()
    val refinementCustomPrompt by prefs.voice.refinementCustomPrompt.collectAsState()

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

        // Saved endpoints
        PreferenceGroup(title = "Saved Endpoints") {
            if (savedEndpoints.isEmpty()) {
                Preference(
                    title = "No saved endpoints",
                    summary = "Add a custom endpoint to use multiple providers",
                )
            } else {
                savedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == activeEndpointId
                    Preference(
                        title = endpoint.name,
                        summary = "${endpoint.baseUrl} • ${endpoint.model}" +
                            if (isActive) " (active)" else "",
                        onClick = { showEndpointActionDialog = endpoint },
                    )
                }
            }
            Preference(
                title = "Add Endpoint",
                onClick = { showAddEndpointDialog = true },
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
                        val client = buildClientFromPrefs(prefs, savedEndpoints, activeEndpointId)
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

        // Text Refinement settings
        PreferenceGroup(title = "Text Refinement") {
            SwitchPreference(
                prefs.voice.refinementEnabled,
                title = "Enable text refinement",
                summary = "Use an LLM to clean up transcribed speech",
            )
            SwitchPreference(
                prefs.voice.refinementAutoRefine,
                title = "Auto-refine",
                summary = "Automatically refine text after transcription",
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
            Preference(
                title = "Refinement Style",
                summary = refinementStyle.displayName(),
                onClick = { showRefinementStyleDialog = true },
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
            Preference(
                title = "Custom Prompt",
                summary = refinementCustomPrompt.ifBlank { "Not set" },
                onClick = { showCustomPromptDialog = true },
                visibleIf = { prefs.voice.refinementStyle isEqualTo RefinementStyle.CUSTOM },
            )
        }

        // LLM Provider settings
        PreferenceGroup(title = "LLM Provider") {
            if (llmSavedEndpoints.isEmpty()) {
                Preference(
                    title = "No LLM endpoints saved",
                    summary = "Add an LLM endpoint for text refinement",
                )
            } else {
                llmSavedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == llmActiveEndpointId
                    Preference(
                        title = endpoint.name,
                        summary = "${endpoint.baseUrl} • ${endpoint.model}" +
                            if (isActive) " (active)" else "",
                        onClick = { showLlmEndpointActionDialog = endpoint },
                    )
                }
            }
            Preference(
                title = "Add LLM Endpoint",
                onClick = { showAddLlmEndpointDialog = true },
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
                            scope.launch { prefs.voice.provider.set(entry) }
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
                scope.launch { currentApiKeyPref.set(apiKey.trim()) }
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
                scope.launch { prefs.voice.customEndpointUrl.set(endpointUrl.trimEnd('/')) }
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
                scope.launch {
                    prefs.voice.model.set(selectedModel)
                    if (provider == VoiceProvider.CUSTOM) {
                        prefs.voice.customModel.set(selectedModel)
                    }
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
                scope.launch { prefs.voice.language.set(languageValue.trim()) }
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

    // Add/Edit endpoint dialog
    if (showAddEndpointDialog || editingEndpoint != null) {
        val isEdit = editingEndpoint != null
        var epName by remember { mutableStateOf(editingEndpoint?.name ?: "") }
        var epUrl by remember { mutableStateOf(editingEndpoint?.baseUrl ?: "") }
        var epApiKey by remember { mutableStateOf(editingEndpoint?.apiKey ?: "") }
        var epModel by remember { mutableStateOf(editingEndpoint?.model ?: "whisper-1") }
        var epValidating by remember { mutableStateOf(false) }
        var epValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
        var epModels by remember { mutableStateOf<List<String>>(emptyList()) }
        var epFetchingModels by remember { mutableStateOf(false) }
        var epModelExpanded by remember { mutableStateOf(false) }
        var epModelsFetched by remember { mutableStateOf(false) }
        var epModelsError by remember { mutableStateOf<String?>(null) }

        fun fetchWhisperModels() {
            if (epUrl.isBlank() || epApiKey.isBlank()) return
            epFetchingModels = true
            epModelsError = null
            epModelsFetched = false
            scope.launch {
                val client = WhisperApiClient(epUrl.trimEnd('/'), epApiKey.trim())
                val result = client.fetchModels()
                if (result.error == null && result.models.isNotEmpty()) {
                    epModels = result.models
                    if (epModel !in result.models && result.models.isNotEmpty()) {
                        epModel = result.models.first()
                    }
                    epModelsFetched = true
                    epModelsError = null
                } else {
                    epModels = emptyList()
                    epModelsFetched = false
                    epModelsError = result.error ?: "No models found"
                }
                epFetchingModels = false
            }
        }

        LaunchedEffect(epUrl, epApiKey) {
            if (epUrl.isNotBlank() && epApiKey.isNotBlank()) {
                delay(800)
                fetchWhisperModels()
            }
        }

        JetPrefAlertDialog(
            title = if (isEdit) "Edit Endpoint" else "Add Endpoint",
            confirmLabel = "Save",
            onConfirm = {
                val id = editingEndpoint?.id ?: java.util.UUID.randomUUID().toString()
                val endpoint = SavedEndpoint(
                    id = id,
                    name = epName.trim(),
                    baseUrl = epUrl.trimEnd('/'),
                    apiKey = epApiKey.trim(),
                    model = epModel.trim(),
                )
                val current = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
                val updated = if (isEdit) {
                    current.map { if (it.id == id) endpoint else it }
                } else {
                    current + endpoint
                }
                scope.launch {
                    prefs.voice.savedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.activeEndpointId.set(id)
                }
                showAddEndpointDialog = false
                editingEndpoint = null
            },
            dismissLabel = "Cancel",
            onDismiss = {
                showAddEndpointDialog = false
                editingEndpoint = null
            },
            confirmEnabled = epName.isNotBlank() && epUrl.isNotBlank() && epApiKey.isNotBlank(),
        ) {
            Column {
                if (!isEdit) {
                    var quickSetupExpanded by remember { mutableStateOf(false) }
                    Text("Quick Setup", modifier = Modifier.padding(bottom = 4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { quickSetupExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Select a provider...")
                        }
                        DropdownMenu(
                            expanded = quickSetupExpanded,
                            onDismissRequest = { quickSetupExpanded = false },
                        ) {
                            PROVIDER_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        epName = preset.name
                                        epUrl = preset.baseUrl
                                        epModel = preset.defaultWhisperModel
                                        quickSetupExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Name", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epName, onValueChange = { epName = it })
                Spacer(modifier = Modifier.height(8.dp))
                Text("Base URL", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epUrl, onValueChange = { epUrl = it })
                Spacer(modifier = Modifier.height(8.dp))
                Text("API Key", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epApiKey, onValueChange = { epApiKey = it })
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Model", modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedButton(
                        onClick = { fetchWhisperModels() },
                        enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epFetchingModels,
                    ) {
                        Text(if (epFetchingModels) "Fetching..." else "Fetch Models")
                    }
                }
                if (epModels.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { epModelExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(epModel.ifBlank { "Select model..." })
                        }
                        DropdownMenu(
                            expanded = epModelExpanded,
                            onDismissRequest = { epModelExpanded = false },
                        ) {
                            epModels.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId, maxLines = 1) },
                                    onClick = {
                                        epModel = modelId
                                        epModelExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "${epModels.size} model(s) available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    JetPrefTextField(value = epModel, onValueChange = { epModel = it })
                    if (epFetchingModels) {
                        Text(
                            "Fetching models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (epModelsError != null && epModelsFetched.not()) {
                        Text(
                            epModelsError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        epValidating = true
                        epValidationResult = null
                        scope.launch {
                            val client = WhisperApiClient(epUrl.trimEnd('/'), epApiKey.trim())
                            epValidationResult = client.validateApiKey()
                            epValidating = false
                        }
                    },
                    enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epValidating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (epValidating) "Validating..." else "Validate Endpoint")
                }
                if (epValidationResult != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (epValidationResult!!.isSuccess) "Connection successful"
                               else epValidationResult!!.errorMessage ?: "Validation failed",
                        color = if (epValidationResult!!.isSuccess) Color(0xFF4CAF50)
                               else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // Endpoint action dialog (Whisper)
    showEndpointActionDialog?.let { endpoint ->
        val isActive = endpoint.id == activeEndpointId
        JetPrefAlertDialog(
            title = endpoint.name,
            confirmLabel = "Close",
            onConfirm = { showEndpointActionDialog = null },
            onDismiss = { showEndpointActionDialog = null },
        ) {
            Column {
                Preference(
                    title = if (isActive) "Deactivate" else "Set Active",
                    summary = if (isActive) "Use a different endpoint" else "Use this endpoint for transcription",
                    onClick = {
                        scope.launch {
                            prefs.voice.activeEndpointId.set(if (isActive) "" else endpoint.id)
                        }
                        showEndpointActionDialog = null
                    },
                )
                Preference(
                    title = "Edit",
                    summary = "Change URL, API key, or model",
                    onClick = {
                        editingEndpoint = endpoint
                        showEndpointActionDialog = null
                    },
                )
                Preference(
                    title = "Delete",
                    summary = "Remove this endpoint",
                    onClick = {
                        showDeleteEndpointConfirm = endpoint
                        showEndpointActionDialog = null
                    },
                )
            }
        }
    }
    showDeleteEndpointConfirm?.let { endpoint ->
        JetPrefAlertDialog(
            title = "Delete Endpoint",
            confirmLabel = "Delete",
            onConfirm = {
                val current = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
                val updated = current.filter { it.id != endpoint.id }
                scope.launch {
                    prefs.voice.savedEndpoints.set(SavedEndpoint.serializeList(updated))
                    if (prefs.voice.activeEndpointId.get() == endpoint.id) {
                        prefs.voice.activeEndpointId.set("")
                    }
                }
                showDeleteEndpointConfirm = null
            },
            dismissLabel = "Cancel",
            onDismiss = { showDeleteEndpointConfirm = null },
        ) {
            Text("Delete \"${endpoint.name}\"?")
        }
    }

    // Refinement style selection dialog
    if (showRefinementStyleDialog) {
        JetPrefAlertDialog(
            title = "Refinement Style",
            confirmLabel = "Cancel",
            onConfirm = { showRefinementStyleDialog = false },
            onDismiss = { showRefinementStyleDialog = false },
        ) {
            Column {
                RefinementStyle.entries.forEach { style ->
                    Preference(
                        title = style.displayName(),
                        summary = style.systemPrompt().take(60) + "...",
                        onClick = {
                            scope.launch { prefs.voice.refinementStyle.set(style) }
                            showRefinementStyleDialog = false
                        },
                    )
                }
            }
        }
    }

    // Custom prompt dialog
    if (showCustomPromptDialog) {
        var promptValue by remember { mutableStateOf(refinementCustomPrompt) }
        JetPrefAlertDialog(
            title = "Custom Refinement Prompt",
            confirmLabel = "Save",
            onConfirm = {
                scope.launch { prefs.voice.refinementCustomPrompt.set(promptValue.trim()) }
                showCustomPromptDialog = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showCustomPromptDialog = false },
        ) {
            Column {
                Text(
                    "Enter the system prompt that will be used to refine transcribed text.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                JetPrefTextField(
                    value = promptValue,
                    onValueChange = { promptValue = it },
                )
            }
        }
    }

    // Add/Edit LLM endpoint dialog
    if (showAddLlmEndpointDialog || editingLlmEndpoint != null) {
        val isEdit = editingLlmEndpoint != null
        var epName by remember { mutableStateOf(editingLlmEndpoint?.name ?: "") }
        var epUrl by remember { mutableStateOf(editingLlmEndpoint?.baseUrl ?: "") }
        var epApiKey by remember { mutableStateOf(editingLlmEndpoint?.apiKey ?: "") }
        var epModel by remember { mutableStateOf(editingLlmEndpoint?.model ?: "gpt-4o-mini") }
        var epValidating by remember { mutableStateOf(false) }
        var epValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
        var epModels by remember { mutableStateOf<List<String>>(emptyList()) }
        var epFetchingModels by remember { mutableStateOf(false) }
        var epModelExpanded by remember { mutableStateOf(false) }
        var epModelsFetched by remember { mutableStateOf(false) }
        var epModelsError by remember { mutableStateOf<String?>(null) }

        fun fetchLlmModels() {
            if (epUrl.isBlank() || epApiKey.isBlank()) return
            epFetchingModels = true
            epModelsError = null
            epModelsFetched = false
            scope.launch {
                val client = LlmApiClient(epUrl.trimEnd('/'), epApiKey.trim(), epModel.trim())
                val result = client.fetchModels()
                if (result.error == null && result.models.isNotEmpty()) {
                    epModels = result.models
                    if (epModel !in result.models && result.models.isNotEmpty()) {
                        epModel = result.models.first()
                    }
                    epModelsFetched = true
                    epModelsError = null
                } else {
                    epModels = emptyList()
                    epModelsFetched = false
                    epModelsError = result.error ?: "No models found"
                }
                epFetchingModels = false
            }
        }

        LaunchedEffect(epUrl, epApiKey) {
            if (epUrl.isNotBlank() && epApiKey.isNotBlank()) {
                delay(800)
                fetchLlmModels()
            }
        }

        JetPrefAlertDialog(
            title = if (isEdit) "Edit LLM Endpoint" else "Add LLM Endpoint",
            confirmLabel = "Save",
            onConfirm = {
                val id = editingLlmEndpoint?.id ?: java.util.UUID.randomUUID().toString()
                val endpoint = SavedEndpoint(
                    id = id,
                    name = epName.trim(),
                    baseUrl = epUrl.trimEnd('/'),
                    apiKey = epApiKey.trim(),
                    model = epModel.trim(),
                )
                val current = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
                val updated = if (isEdit) {
                    current.map { if (it.id == id) endpoint else it }
                } else {
                    current + endpoint
                }
                scope.launch {
                    prefs.voice.llmSavedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.llmActiveEndpointId.set(id)
                }
                showAddLlmEndpointDialog = false
                editingLlmEndpoint = null
            },
            dismissLabel = "Cancel",
            onDismiss = {
                showAddLlmEndpointDialog = false
                editingLlmEndpoint = null
            },
            confirmEnabled = epName.isNotBlank() && epUrl.isNotBlank() && epApiKey.isNotBlank(),
        ) {
            Column {
                if (!isEdit) {
                    var quickSetupExpanded by remember { mutableStateOf(false) }
                    Text("Quick Setup", modifier = Modifier.padding(bottom = 4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { quickSetupExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Select a provider...")
                        }
                        DropdownMenu(
                            expanded = quickSetupExpanded,
                            onDismissRequest = { quickSetupExpanded = false },
                        ) {
                            PROVIDER_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        epName = preset.name
                                        epUrl = preset.baseUrl
                                        epModel = preset.defaultLlmModel
                                        quickSetupExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Name", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epName, onValueChange = { epName = it })
                Spacer(modifier = Modifier.height(8.dp))
                Text("Base URL", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epUrl, onValueChange = { epUrl = it })
                Spacer(modifier = Modifier.height(8.dp))
                Text("API Key", modifier = Modifier.padding(bottom = 4.dp))
                JetPrefTextField(value = epApiKey, onValueChange = { epApiKey = it })
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Model", modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedButton(
                        onClick = { fetchLlmModels() },
                        enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epFetchingModels,
                    ) {
                        Text(if (epFetchingModels) "Fetching..." else "Fetch Models")
                    }
                }
                if (epModels.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { epModelExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(epModel.ifBlank { "Select model..." })
                        }
                        DropdownMenu(
                            expanded = epModelExpanded,
                            onDismissRequest = { epModelExpanded = false },
                        ) {
                            epModels.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId, maxLines = 1) },
                                    onClick = {
                                        epModel = modelId
                                        epModelExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "${epModels.size} model(s) available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    JetPrefTextField(value = epModel, onValueChange = { epModel = it })
                    if (epFetchingModels) {
                        Text(
                            "Fetching models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (epModelsError != null && epModelsFetched.not()) {
                        Text(
                            epModelsError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        epValidating = true
                        epValidationResult = null
                        scope.launch {
                            val client = LlmApiClient(epUrl.trimEnd('/'), epApiKey.trim(), epModel.trim())
                            epValidationResult = client.validateApiKey()
                            epValidating = false
                        }
                    },
                    enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epValidating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (epValidating) "Validating..." else "Validate Endpoint")
                }
                if (epValidationResult != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (epValidationResult!!.isSuccess) "Connection successful"
                               else epValidationResult!!.errorMessage ?: "Validation failed",
                        color = if (epValidationResult!!.isSuccess) Color(0xFF4CAF50)
                               else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // Delete LLM endpoint confirm dialog
    // LLM endpoint action dialog
    showLlmEndpointActionDialog?.let { endpoint ->
        val isActive = endpoint.id == llmActiveEndpointId
        JetPrefAlertDialog(
            title = endpoint.name,
            confirmLabel = "Close",
            onConfirm = { showLlmEndpointActionDialog = null },
            onDismiss = { showLlmEndpointActionDialog = null },
        ) {
            Column {
                Preference(
                    title = if (isActive) "Deactivate" else "Set Active",
                    summary = if (isActive) "Use a different LLM endpoint" else "Use this endpoint for refinement",
                    onClick = {
                        scope.launch {
                            prefs.voice.llmActiveEndpointId.set(if (isActive) "" else endpoint.id)
                        }
                        showLlmEndpointActionDialog = null
                    },
                )
                Preference(
                    title = "Edit",
                    summary = "Change URL, API key, or model",
                    onClick = {
                        editingLlmEndpoint = endpoint
                        showLlmEndpointActionDialog = null
                    },
                )
                Preference(
                    title = "Delete",
                    summary = "Remove this endpoint",
                    onClick = {
                        showDeleteLlmEndpointConfirm = endpoint
                        showLlmEndpointActionDialog = null
                    },
                )
            }
        }
    }

    showDeleteLlmEndpointConfirm?.let { endpoint ->
        JetPrefAlertDialog(
            title = "Delete LLM Endpoint",
            confirmLabel = "Delete",
            onConfirm = {
                val current = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
                val updated = current.filter { it.id != endpoint.id }
                scope.launch {
                    prefs.voice.llmSavedEndpoints.set(SavedEndpoint.serializeList(updated))
                    if (prefs.voice.llmActiveEndpointId.get() == endpoint.id) {
                        prefs.voice.llmActiveEndpointId.set("")
                    }
                }
                showDeleteLlmEndpointConfirm = null
            },
            dismissLabel = "Cancel",
            onDismiss = { showDeleteLlmEndpointConfirm = null },
        ) {
            Text("Delete \"${endpoint.name}\"?")
        }
    }
}

private fun buildClientFromPrefs(
    prefs: FlorisPreferenceModel,
    savedEndpoints: List<SavedEndpoint>,
    activeEndpointId: String,
): WhisperApiClient {
    if (activeEndpointId.isNotBlank()) {
        val active = savedEndpoints.find { it.id == activeEndpointId }
        if (active != null) {
            return WhisperApiClient(
                baseUrl = active.baseUrl.trimEnd('/'),
                apiKey = active.apiKey,
            )
        }
    }
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
