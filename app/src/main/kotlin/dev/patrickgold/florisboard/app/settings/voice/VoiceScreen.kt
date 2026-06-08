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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.voice.LlmApiClient
import dev.patrickgold.florisboard.ime.voice.ModelsResult
import dev.patrickgold.florisboard.ime.voice.RefinementStyle
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

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun VoiceScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voice__title)
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
    var showRefinementStyleDialog by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var showAddLlmEndpointDialog by remember { mutableStateOf(false) }
    var editingLlmEndpoint by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showDeleteLlmEndpointConfirm by remember { mutableStateOf<SavedEndpoint?>(null) }

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
        PreferenceGroup(title = stringRes(R.string.settings__voice__provider_group)) {
            Preference(
                title = stringRes(R.string.settings__voice__speech_to_text_provider),
                summary = when (provider) {
                    VoiceProvider.OPENAI -> stringRes(R.string.settings__voice__provider_openai)
                    VoiceProvider.GROQ -> stringRes(R.string.settings__voice__provider_groq)
                    VoiceProvider.CUSTOM -> stringRes(R.string.settings__voice__provider_custom)
                },
                onClick = { showProviderDialog = true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__saved_endpoints_group)) {
            if (savedEndpoints.isEmpty()) {
                Preference(
                    title = stringRes(R.string.settings__voice__no_saved_endpoints),
                    summary = stringRes(R.string.settings__voice__no_saved_endpoints_summary),
                )
            } else {
                savedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == activeEndpointId
                    EndpointItem(
                        name = endpoint.name,
                        subtitle = "${endpoint.baseUrl} • ${endpoint.model}" +
                            if (isActive) stringRes(R.string.settings__voice__active_suffix) else "",
                        isActive = isActive,
                        onClick = {
                            scope.launch {
                                if (isActive) {
                                    prefs.voice.activeEndpointId.set("")
                                } else {
                                    prefs.voice.activeEndpointId.set(endpoint.id)
                                }
                            }
                        },
                        onEdit = { editingEndpoint = endpoint },
                        onDelete = { showDeleteEndpointConfirm = endpoint },
                    )
                }
            }
            Preference(
                title = stringRes(R.string.settings__voice__add_endpoint),
                onClick = { showAddEndpointDialog = true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__api_config_group)) {
            Preference(
                title = when (provider) {
                    VoiceProvider.OPENAI -> stringRes(R.string.settings__voice__api_key_openai)
                    VoiceProvider.GROQ -> stringRes(R.string.settings__voice__api_key_groq)
                    VoiceProvider.CUSTOM -> stringRes(R.string.settings__voice__api_key_custom)
                },
                summary = if (currentApiKey.isNotBlank()) {
                    "${currentApiKey.take(4)}${"*".repeat(12)}"
                } else {
                    stringRes(R.string.settings__voice__not_set)
                },
                onClick = { showApiKeyDialog = true },
            )

            Preference(
                title = stringRes(R.string.settings__voice__custom_endpoint_url),
                summary = customEndpointUrl.ifBlank { stringRes(R.string.settings__voice__not_set) },
                onClick = { showEndpointDialog = true },
                visibleIf = { prefs.voice.provider isEqualTo VoiceProvider.CUSTOM },
            )

            Preference(
                title = stringRes(R.string.settings__voice__validate_api_key),
                summary = when {
                    isValidating -> stringRes(R.string.settings__voice__checking)
                    validationResult?.isSuccess == true -> stringRes(R.string.settings__voice__connection_successful)
                    validationResult?.isSuccess == false -> validationResult?.errorMessage
                    else -> stringRes(R.string.settings__voice__validate_summary)
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

        PreferenceGroup(title = stringRes(R.string.settings__voice__model_group)) {
            Preference(
                title = stringRes(R.string.settings__voice__model),
                summary = model.ifBlank { stringRes(R.string.settings__voice__not_set) },
                onClick = { showModelDialog = true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__transcription_group)) {
            Preference(
                title = stringRes(R.string.settings__voice__language),
                summary = language.ifBlank { stringRes(R.string.settings__voice__auto_detect) },
                onClick = { showLanguageDialog = true },
            )

            SwitchPreference(
                prefs.voice.autoCommit,
                title = stringRes(R.string.settings__voice__auto_commit),
                summary = stringRes(R.string.settings__voice__auto_commit_summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__refinement_group)) {
            SwitchPreference(
                prefs.voice.refinementEnabled,
                title = stringRes(R.string.settings__voice__enable_refinement),
                summary = stringRes(R.string.settings__voice__enable_refinement_summary),
            )
            SwitchPreference(
                prefs.voice.refinementAutoRefine,
                title = stringRes(R.string.settings__voice__auto_refine),
                summary = stringRes(R.string.settings__voice__auto_refine_summary),
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
            Preference(
                title = stringRes(R.string.settings__voice__refinement_style),
                summary = refinementStyle.displayName(),
                onClick = { showRefinementStyleDialog = true },
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
            Preference(
                title = if (refinementStyle == RefinementStyle.CUSTOM) {
                    stringRes(R.string.settings__voice__custom_prompt)
                } else {
                    stringRes(R.string.settings__voice__customize_prompt)
                },
                summary = if (refinementCustomPrompt.isNotBlank()) {
                    refinementCustomPrompt
                } else if (refinementStyle == RefinementStyle.CUSTOM) {
                    stringRes(R.string.settings__voice__not_set)
                } else {
                    refinementStyle.shortDescription()
                },
                onClick = { showCustomPromptDialog = true },
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__llm_provider_group)) {
            if (llmSavedEndpoints.isEmpty()) {
                Preference(
                    title = stringRes(R.string.settings__voice__no_llm_endpoints),
                    summary = stringRes(R.string.settings__voice__no_llm_endpoints_summary),
                )
            } else {
                llmSavedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == llmActiveEndpointId
                    EndpointItem(
                        name = endpoint.name,
                        subtitle = "${endpoint.baseUrl} • ${endpoint.model}" +
                            if (isActive) stringRes(R.string.settings__voice__active_suffix) else "",
                        isActive = isActive,
                        onClick = {
                            scope.launch {
                                if (isActive) {
                                    prefs.voice.llmActiveEndpointId.set("")
                                } else {
                                    prefs.voice.llmActiveEndpointId.set(endpoint.id)
                                }
                            }
                        },
                        onEdit = { editingLlmEndpoint = endpoint },
                        onDelete = { showDeleteLlmEndpointConfirm = endpoint },
                    )
                }
            }
            Preference(
                title = stringRes(R.string.settings__voice__add_llm_endpoint),
                onClick = { showAddLlmEndpointDialog = true },
            )
        }
    }

    // Provider selection dialog
    if (showProviderDialog) {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__voice__select_provider),
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showProviderDialog = false },
        ) {
            Column {
                VoiceProvider.entries.forEach { entry ->
                    Preference(
                        title = when (entry) {
                            VoiceProvider.OPENAI -> stringRes(R.string.settings__voice__provider_openai)
                            VoiceProvider.GROQ -> stringRes(R.string.settings__voice__provider_groq)
                            VoiceProvider.CUSTOM -> stringRes(R.string.settings__voice__provider_custom)
                        },
                        summary = when (entry) {
                            VoiceProvider.OPENAI -> stringRes(R.string.settings__voice__provider_openai_summary)
                            VoiceProvider.GROQ -> stringRes(R.string.settings__voice__provider_groq_summary)
                            VoiceProvider.CUSTOM -> stringRes(R.string.settings__voice__provider_custom_summary)
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
                VoiceProvider.OPENAI -> stringRes(R.string.settings__voice__api_key_openai)
                VoiceProvider.GROQ -> stringRes(R.string.settings__voice__api_key_groq)
                VoiceProvider.CUSTOM -> stringRes(R.string.settings__voice__api_key_custom)
            },
            confirmLabel = stringRes(R.string.settings__voice__save),
            onConfirm = {
                scope.launch { currentApiKeyPref.set(apiKey.trim()) }
                showApiKeyDialog = false
                validationResult = null
            },
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showApiKeyDialog = false },
        ) {
            JetPrefTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
            )
        }
    }

    // Custom Endpoint URL dialog
    if (showEndpointDialog) {
        var endpointUrl by remember { mutableStateOf(customEndpointUrl) }
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__voice__custom_endpoint_url),
            confirmLabel = stringRes(R.string.settings__voice__save),
            onConfirm = {
                scope.launch { prefs.voice.customEndpointUrl.set(endpointUrl.trimEnd('/')) }
                showEndpointDialog = false
                validationResult = null
            },
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showEndpointDialog = false },
        ) {
            Column {
                Text(
                    stringRes(R.string.settings__voice__endpoint_url_hint),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                JetPrefTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringRes(R.string.settings__voice__endpoint_url_example),
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
            title = stringRes(R.string.settings__voice__select_model),
            confirmLabel = stringRes(R.string.settings__voice__save),
            onConfirm = {
                scope.launch {
                    prefs.voice.model.set(selectedModel)
                    if (provider == VoiceProvider.CUSTOM) {
                        prefs.voice.customModel.set(selectedModel)
                    }
                }
                showModelDialog = false
            },
            dismissLabel = stringRes(R.string.settings__voice__cancel),
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
                    stringRes(R.string.settings__voice__custom_model_hint),
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
            title = stringRes(R.string.settings__voice__language),
            confirmLabel = stringRes(R.string.settings__voice__save),
            onConfirm = {
                scope.launch { prefs.voice.language.set(languageValue.trim()) }
                showLanguageDialog = false
            },
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showLanguageDialog = false },
        ) {
            Column {
                Text(
                    stringRes(R.string.settings__voice__language_hint),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    stringRes(R.string.settings__voice__language_auto_hint),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                JetPrefTextField(
                    value = languageValue,
                    onValueChange = { languageValue = it },
                )
            }
        }
    }

    // Add/Edit Whisper endpoint dialog (shared)
    if (showAddEndpointDialog || editingEndpoint != null) {
        EndpointEditorDialog(
            isEdit = editingEndpoint != null,
            existingEndpoint = editingEndpoint,
            defaultModel = "whisper-1",
            fetchModels = { url, key -> WhisperApiClient(url, key).fetchModels() },
            validateEndpoint = { url, key -> WhisperApiClient(url, key).validateApiKey() },
            onSave = { endpoint ->
                val current = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
                val updated = if (editingEndpoint != null) {
                    current.map { if (it.id == endpoint.id) endpoint else it }
                } else {
                    current + endpoint
                }
                scope.launch {
                    prefs.voice.savedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.activeEndpointId.set(endpoint.id)
                }
            },
            onDismiss = {
                showAddEndpointDialog = false
                editingEndpoint = null
            },
            editTitle = stringRes(R.string.settings__voice__edit_endpoint),
            addTitle = stringRes(R.string.settings__voice__add_endpoint_dialog),
        )
    }

    // Delete endpoint confirm dialog
    showDeleteEndpointConfirm?.let { endpoint ->
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__voice__delete_endpoint),
            confirmLabel = stringRes(R.string.settings__voice__delete),
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
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showDeleteEndpointConfirm = null },
        ) {
            Text(stringResource(R.string.settings__voice__delete_confirm, endpoint.name))
        }
    }

    // Refinement style selection dialog
    if (showRefinementStyleDialog) {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__voice__refinement_style_dialog),
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showRefinementStyleDialog = false },
        ) {
            Column {
                RefinementStyle.entries.forEach { style ->
                    Preference(
                        title = style.displayName(),
                        summary = style.shortDescription(),
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
        val isOverride = refinementStyle != RefinementStyle.CUSTOM
        JetPrefAlertDialog(
            title = if (isOverride) stringRes(R.string.settings__voice__customize_prompt)
                    else stringRes(R.string.settings__voice__custom_prompt_dialog),
            confirmLabel = stringRes(R.string.settings__voice__save),
            onConfirm = {
                scope.launch { prefs.voice.refinementCustomPrompt.set(promptValue.trim()) }
                showCustomPromptDialog = false
            },
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showCustomPromptDialog = false },
        ) {
            Column {
                Text(
                    if (isOverride) stringRes(R.string.settings__voice__customize_prompt_hint)
                    else stringRes(R.string.settings__voice__custom_prompt_hint),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (isOverride && promptValue.isBlank()) {
                    Text(
                        "Default: ${refinementStyle.systemPrompt().take(80)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                JetPrefTextField(
                    value = promptValue,
                    onValueChange = { promptValue = it },
                )
            }
        }
    }

    // Add/Edit LLM endpoint dialog (shared)
    if (showAddLlmEndpointDialog || editingLlmEndpoint != null) {
        EndpointEditorDialog(
            isEdit = editingLlmEndpoint != null,
            existingEndpoint = editingLlmEndpoint,
            defaultModel = "gpt-4o-mini",
            fetchModels = { url, key -> LlmApiClient(url, key, "").fetchModels() },
            validateEndpoint = { url, key -> LlmApiClient(url, key, "").validateApiKey() },
            onSave = { endpoint ->
                val current = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
                val updated = if (editingLlmEndpoint != null) {
                    current.map { if (it.id == endpoint.id) endpoint else it }
                } else {
                    current + endpoint
                }
                scope.launch {
                    prefs.voice.llmSavedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.llmActiveEndpointId.set(endpoint.id)
                }
            },
            onDismiss = {
                showAddLlmEndpointDialog = false
                editingLlmEndpoint = null
            },
            editTitle = stringRes(R.string.settings__voice__edit_llm_endpoint),
            addTitle = stringRes(R.string.settings__voice__add_llm_endpoint_dialog),
        )
    }

    // Delete LLM endpoint confirm dialog
    showDeleteLlmEndpointConfirm?.let { endpoint ->
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__voice__delete_llm_endpoint),
            confirmLabel = stringRes(R.string.settings__voice__delete),
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
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showDeleteLlmEndpointConfirm = null },
        ) {
            Text(stringRes(R.string.settings__voice__delete_confirm, endpoint.name))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared endpoint editor dialog — used for both Whisper and LLM endpoints
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EndpointEditorDialog(
    isEdit: Boolean,
    existingEndpoint: SavedEndpoint?,
    defaultModel: String,
    fetchModels: suspend (baseUrl: String, apiKey: String) -> ModelsResult,
    validateEndpoint: suspend (baseUrl: String, apiKey: String) -> ValidationResult,
    onSave: (SavedEndpoint) -> Unit,
    onDismiss: () -> Unit,
    editTitle: String,
    addTitle: String,
) {
    val scope = rememberCoroutineScope()
    var epName by remember { mutableStateOf(existingEndpoint?.name ?: "") }
    var epUrl by remember { mutableStateOf(existingEndpoint?.baseUrl ?: "") }
    var epApiKey by remember { mutableStateOf(existingEndpoint?.apiKey ?: "") }
    var epModel by remember { mutableStateOf(existingEndpoint?.model ?: defaultModel) }
    var epValidating by remember { mutableStateOf(false) }
    var epValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
    var epModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var epFetchingModels by remember { mutableStateOf(false) }
    var epModelExpanded by remember { mutableStateOf(false) }
    var epModelsFetched by remember { mutableStateOf(false) }
    var epModelsError by remember { mutableStateOf<String?>(null) }

    fun doFetchModels() {
        if (epUrl.isBlank() || epApiKey.isBlank()) return
        epFetchingModels = true
        epModelsError = null
        epModelsFetched = false
        scope.launch {
            val result = fetchModels(epUrl.trimEnd('/'), epApiKey.trim())
            if (result.error == null && result.models.isNotEmpty()) {
                epModels = result.models
                if (epModel !in result.models) {
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

    JetPrefAlertDialog(
        title = if (isEdit) editTitle else addTitle,
        confirmLabel = stringRes(R.string.settings__voice__save),
        onConfirm = {
            val id = existingEndpoint?.id ?: java.util.UUID.randomUUID().toString()
            onSave(SavedEndpoint(
                id = id,
                name = epName.trim(),
                baseUrl = epUrl.trimEnd('/'),
                apiKey = epApiKey.trim(),
                model = epModel.trim(),
            ))
            onDismiss()
        },
        dismissLabel = stringRes(R.string.settings__voice__cancel),
        onDismiss = onDismiss,
        confirmEnabled = epName.isNotBlank() && epUrl.isNotBlank() && epApiKey.isNotBlank(),
    ) {
        Column {
            Text(stringRes(R.string.settings__voice__endpoint_name), modifier = Modifier.padding(bottom = 4.dp))
            JetPrefTextField(value = epName, onValueChange = { epName = it })
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringRes(R.string.settings__voice__endpoint_base_url), modifier = Modifier.padding(bottom = 4.dp))
            JetPrefTextField(value = epUrl, onValueChange = { epUrl = it })
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringRes(R.string.settings__voice__endpoint_api_key), modifier = Modifier.padding(bottom = 4.dp))
            JetPrefTextField(value = epApiKey, onValueChange = { epApiKey = it })
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringRes(R.string.settings__voice__model), modifier = Modifier.padding(bottom = 4.dp))
                OutlinedButton(
                    onClick = { doFetchModels() },
                    enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epFetchingModels,
                ) {
                    Text(
                        if (epFetchingModels) stringRes(R.string.settings__voice__fetching)
                        else stringRes(R.string.settings__voice__fetch_models),
                    )
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
                    stringResource(R.string.settings__voice__models_count, epModels.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                JetPrefTextField(value = epModel, onValueChange = { epModel = it })
                if (epFetchingModels) {
                    Text(
                        stringRes(R.string.settings__voice__fetching_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (epModelsError != null && !epModelsFetched) {
                    Text(
                        epModelsError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    epValidating = true
                    epValidationResult = null
                    scope.launch {
                        epValidationResult = validateEndpoint(epUrl.trimEnd('/'), epApiKey.trim())
                        epValidating = false
                    }
                },
                enabled = epUrl.isNotBlank() && epApiKey.isNotBlank() && !epValidating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (epValidating) stringRes(R.string.settings__voice__validating)
                    else stringRes(R.string.settings__voice__validate_endpoint),
                )
            }
            if (epValidationResult != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (epValidationResult!!.isSuccess) stringRes(R.string.settings__voice__connection_successful)
                           else epValidationResult!!.errorMessage
                               ?: stringRes(R.string.settings__voice__validation_failed),
                    color = if (epValidationResult!!.isSuccess) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════════
//  Endpoint list item with edit/delete actions
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EndpointItem(
    name: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringRes(R.string.settings__voice__edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringRes(R.string.settings__voice__delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun RefinementStyle.shortDescription(): String = when (this) {
    RefinementStyle.CLEAN_UP -> "Remove filler words and clean up grammar"
    RefinementStyle.CASUAL -> "Relaxed, conversational tone"
    RefinementStyle.FORMAL -> "Professional and polished"
    RefinementStyle.PROFESSIONAL -> "Business-appropriate language"
    RefinementStyle.ACADEMIC -> "Scholarly tone with precise vocabulary"
    RefinementStyle.CONCISE -> "Shorten while keeping key meaning"
    RefinementStyle.BULLET_POINTS -> "Convert to organized bullet points"
    RefinementStyle.AGENT -> "Generate content from voice instructions"
    RefinementStyle.CUSTOM -> "Use your own custom system prompt"
}


