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

package com.voxkb.app.settings.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxkb.R
import com.voxkb.lib.compose.stringRes
import com.voxkb.app.VoxKBAppActivity
import com.voxkb.app.VoxKBPreferenceModel
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.ime.voice.LlmApiClient
import com.voxkb.ime.voice.LLM_PRESETS
import com.voxkb.ime.voice.ModelsResult
import com.voxkb.ime.voice.ProviderPreset
import com.voxkb.ime.voice.RefinementStyle
import com.voxkb.ime.voice.STT_PRESETS
import com.voxkb.ime.voice.VoiceAnimationStyle
import com.voxkb.ime.voice.SavedEndpoint
import com.voxkb.ime.voice.ValidationResult
import com.voxkb.ime.voice.WhisperApiClient
import com.voxkb.lib.compose.VoxKBScreen
import com.voxkb.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun VoiceScreen() = VoxKBScreen {
    title = stringRes(R.string.settings__voice__title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by VoxKBPreferenceStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    val refinementStyle by prefs.voice.refinementStyle.collectAsState()
    val refinementCustomPrompt by prefs.voice.refinementCustomPrompt.collectAsState()
    val animationStyle by prefs.voice.animationStyle.collectAsState()

    var showAddEndpointDialog by remember { mutableStateOf(false) }
    var preselectedSttPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var editingEndpoint by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showDeleteEndpointConfirm by remember { mutableStateOf<SavedEndpoint?>(null) }

    var showAddLlmEndpointDialog by remember { mutableStateOf(false) }
    var preselectedLlmPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var editingLlmEndpoint by remember { mutableStateOf<SavedEndpoint?>(null) }
    var showDeleteLlmEndpointConfirm by remember { mutableStateOf<SavedEndpoint?>(null) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRefinementStyleDialog by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var showAnimationStyleDialog by remember { mutableStateOf(false) }

    val language by prefs.voice.language.collectAsState()

    content {
        val hasMicPermission = context.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.settings__voice__microphone_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings__voice__grant_microphone),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.settings__voice__microphone_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, VoxKBAppActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                action = "REQUEST_RECORD_AUDIO"
                            }
                            context.startActivity(intent)
                        },
                    ) {
                        Text(stringRes(R.string.settings__voice__grant_microphone))
                    }
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__saved_endpoints_group)) {
            if (savedEndpoints.isEmpty()) {
                EmptyEndpointsHint(
                    message = stringRes(R.string.settings__voice__no_saved_endpoints_summary)
                )
            } else {
                savedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == activeEndpointId
                    EndpointRow(
                        endpoint = endpoint,
                        isActive = isActive,
                        onToggleActive = {
                            scope.launch {
                                prefs.voice.activeEndpointId.set(
                                    if (isActive) "" else endpoint.id
                                )
                            }
                        },
                        onEdit = { editingEndpoint = endpoint },
                        onDelete = { showDeleteEndpointConfirm = endpoint },
                    )
                }
            }
            Preference(
                title = stringRes(R.string.settings__voice__add_endpoint),
                icon = Icons.Filled.Add,
                onClick = {
                    preselectedSttPreset = null
                    showAddEndpointDialog = true
                },
            )
        }

        val activeSttPresetIds = savedEndpoints.mapNotNull { ep ->
            STT_PRESETS.find { it.baseUrl == ep.baseUrl }?.id
        }
        PreferenceGroup(title = stringRes(R.string.settings__voice__stt_quick_add)) {
            ProviderPresetGrid(
                presets = STT_PRESETS,
                activePresetIds = activeSttPresetIds,
                onSelect = { preset ->
                    preselectedSttPreset = preset
                    showAddEndpointDialog = true
                },
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
            SwitchPreference(
                prefs.voice.reviewBeforeInsert,
                title = "Review before insert",
                summary = "Show transcript for review before inserting into text field",
            )
            Preference(
                title = "Animation Style",
                summary = animationStyle.displayName(),
                onClick = { showAnimationStyleDialog = true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice__llm_provider_group)) {
            if (llmSavedEndpoints.isEmpty()) {
                EmptyEndpointsHint(
                    message = stringRes(R.string.settings__voice__no_llm_endpoints_summary)
                )
            } else {
                llmSavedEndpoints.forEach { endpoint ->
                    val isActive = endpoint.id == llmActiveEndpointId
                    EndpointRow(
                        endpoint = endpoint,
                        isActive = isActive,
                        onToggleActive = {
                            scope.launch {
                                prefs.voice.llmActiveEndpointId.set(
                                    if (isActive) "" else endpoint.id
                                )
                            }
                        },
                        onEdit = { editingLlmEndpoint = endpoint },
                        onDelete = { showDeleteLlmEndpointConfirm = endpoint },
                    )
                }
            }
            Preference(
                title = stringRes(R.string.settings__voice__add_llm_endpoint),
                icon = Icons.Filled.Add,
                onClick = {
                    preselectedLlmPreset = null
                    showAddLlmEndpointDialog = true
                },
            )
        }

        val activeLlmPresetIds = llmSavedEndpoints.mapNotNull { ep ->
            LLM_PRESETS.find { it.baseUrl == ep.baseUrl }?.id
        }
        PreferenceGroup(title = stringRes(R.string.settings__voice__llm_quick_add)) {
            ProviderPresetGrid(
                presets = LLM_PRESETS,
                activePresetIds = activeLlmPresetIds,
                onSelect = { preset ->
                    preselectedLlmPreset = preset
                    showAddLlmEndpointDialog = true
                },
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
                title = if (refinementStyle == RefinementStyle.CUSTOM)
                    stringRes(R.string.settings__voice__custom_prompt)
                else
                    stringRes(R.string.settings__voice__customize_prompt),
                summary = refinementCustomPrompt.ifBlank {
                    if (refinementStyle == RefinementStyle.CUSTOM)
                        stringRes(R.string.settings__voice__not_set)
                    else
                        refinementStyle.shortDescription()
                },
                onClick = { showCustomPromptDialog = true },
                visibleIf = { prefs.voice.refinementEnabled isEqualTo true },
            )
        }
    }

    if (showAddEndpointDialog || editingEndpoint != null) {
        EndpointEditorDialog(
            isEdit = editingEndpoint != null,
            existingEndpoint = editingEndpoint,
            preset = if (editingEndpoint != null) null else preselectedSttPreset,
            defaultModel = preselectedSttPreset?.defaultModel ?: "whisper-1",
            fetchModels = { url, key -> WhisperApiClient(url, key).fetchModels() },
            validateEndpoint = { url, key -> WhisperApiClient(url, key).validateApiKey() },
            onSave = { endpoint ->
                val current = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
                val updated = if (editingEndpoint != null)
                    current.map { if (it.id == endpoint.id) endpoint else it }
                else
                    current + endpoint
                scope.launch {
                    prefs.voice.savedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.activeEndpointId.set(endpoint.id)
                }
            },
            onDismiss = {
                showAddEndpointDialog = false
                editingEndpoint = null
                preselectedSttPreset = null
            },
            editTitle = stringRes(R.string.settings__voice__edit_endpoint),
            addTitle = stringRes(R.string.settings__voice__add_endpoint_dialog),
        )
    }

    showDeleteEndpointConfirm?.let { endpoint ->
        DeleteConfirmDialog(
            name = endpoint.name,
            titleRes = R.string.settings__voice__delete_endpoint,
            onConfirm = {
                val current = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
                scope.launch {
                    prefs.voice.savedEndpoints.set(
                        SavedEndpoint.serializeList(current.filter { it.id != endpoint.id })
                    )
                    if (prefs.voice.activeEndpointId.get() == endpoint.id) {
                        prefs.voice.activeEndpointId.set("")
                    }
                }
                showDeleteEndpointConfirm = null
            },
            onDismiss = { showDeleteEndpointConfirm = null },
        )
    }

    if (showAddLlmEndpointDialog || editingLlmEndpoint != null) {
        EndpointEditorDialog(
            isEdit = editingLlmEndpoint != null,
            existingEndpoint = editingLlmEndpoint,
            preset = if (editingLlmEndpoint != null) null else preselectedLlmPreset,
            defaultModel = preselectedLlmPreset?.defaultModel ?: "gpt-4o-mini",
            fetchModels = { url, key -> LlmApiClient(url, key, "").fetchModels() },
            validateEndpoint = { url, key -> LlmApiClient(url, key, "").validateApiKey() },
            onSave = { endpoint ->
                val current = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
                val updated = if (editingLlmEndpoint != null)
                    current.map { if (it.id == endpoint.id) endpoint else it }
                else
                    current + endpoint
                scope.launch {
                    prefs.voice.llmSavedEndpoints.set(SavedEndpoint.serializeList(updated))
                    prefs.voice.llmActiveEndpointId.set(endpoint.id)
                }
            },
            onDismiss = {
                showAddLlmEndpointDialog = false
                editingLlmEndpoint = null
                preselectedLlmPreset = null
            },
            editTitle = stringRes(R.string.settings__voice__edit_llm_endpoint),
            addTitle = stringRes(R.string.settings__voice__add_llm_endpoint_dialog),
        )
    }

    showDeleteLlmEndpointConfirm?.let { endpoint ->
        DeleteConfirmDialog(
            name = endpoint.name,
            titleRes = R.string.settings__voice__delete_llm_endpoint,
            onConfirm = {
                val current = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
                scope.launch {
                    prefs.voice.llmSavedEndpoints.set(
                        SavedEndpoint.serializeList(current.filter { it.id != endpoint.id })
                    )
                    if (prefs.voice.llmActiveEndpointId.get() == endpoint.id) {
                        prefs.voice.llmActiveEndpointId.set("")
                    }
                }
                showDeleteLlmEndpointConfirm = null
            },
            onDismiss = { showDeleteLlmEndpointConfirm = null },
        )
    }

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
                JetPrefTextField(value = languageValue, onValueChange = { languageValue = it })
            }
        }
    }

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

    if (showCustomPromptDialog) {
        val refinementStyleSnapshot by prefs.voice.refinementStyle.collectAsState()
        val refinementCustomPromptSnapshot by prefs.voice.refinementCustomPrompt.collectAsState()
        var promptValue by remember { mutableStateOf(refinementCustomPromptSnapshot) }
        val isOverride = refinementStyleSnapshot != RefinementStyle.CUSTOM

        JetPrefAlertDialog(
            title = if (isOverride)
                stringRes(R.string.settings__voice__customize_prompt)
            else
                stringRes(R.string.settings__voice__custom_prompt_dialog),
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
                        "Default: ${refinementStyleSnapshot.systemPrompt().take(120)}\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                JetPrefTextField(value = promptValue, onValueChange = { promptValue = it })
            }
        }
    }

    if (showAnimationStyleDialog) {
        JetPrefAlertDialog(
            title = "Animation Style",
            dismissLabel = stringRes(R.string.settings__voice__cancel),
            onDismiss = { showAnimationStyleDialog = false },
        ) {
            Column {
                VoiceAnimationStyle.entries.forEach { style ->
                    Preference(
                        title = style.displayName(),
                        summary = style.shortDescription(),
                        onClick = {
                            scope.launch { prefs.voice.animationStyle.set(style) }
                            showAnimationStyleDialog = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPresetGrid(
    presets: List<ProviderPreset>,
    activePresetIds: List<String>,
    onSelect: (ProviderPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPresets.forEach { preset ->
                    val alreadyAdded = preset.id in activePresetIds
                    ProviderPresetCard(
                        preset = preset,
                        alreadyAdded = alreadyAdded,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(preset) },
                    )
                }
                if (rowPresets.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProviderPresetCard(
    preset: ProviderPreset,
    alreadyAdded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (alreadyAdded)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = if (alreadyAdded) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alreadyAdded)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(preset.iconRes),
                    contentDescription = "${preset.name} logo",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                if (alreadyAdded) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EndpointRow(
    endpoint: SavedEndpoint,
    isActive: Boolean,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleActive),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = endpoint.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = stringResource(R.string.settings__voice__preset_added),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "${endpoint.baseUrl} \u00b7 ${endpoint.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringRes(R.string.settings__voice__edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringRes(R.string.settings__voice__delete_desc),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyEndpointsHint(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EndpointEditorDialog(
    isEdit: Boolean,
    existingEndpoint: SavedEndpoint?,
    preset: ProviderPreset?,
    defaultModel: String,
    fetchModels: suspend (baseUrl: String, apiKey: String) -> ModelsResult,
    validateEndpoint: suspend (baseUrl: String, apiKey: String) -> ValidationResult,
    onSave: (SavedEndpoint) -> Unit,
    onDismiss: () -> Unit,
    editTitle: String,
    addTitle: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var epName by remember {
        mutableStateOf(existingEndpoint?.name ?: preset?.name ?: "")
    }
    var epUrl by remember {
        mutableStateOf(existingEndpoint?.baseUrl ?: preset?.baseUrl ?: "")
    }
    var epApiKey by remember {
        mutableStateOf(existingEndpoint?.apiKey ?: "")
    }
    var epModel by remember {
        mutableStateOf(existingEndpoint?.model ?: preset?.defaultModel ?: defaultModel)
    }

    var epValidating by remember { mutableStateOf(false) }
    var epValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
    var epModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var epFetchingModels by remember { mutableStateOf(false) }
    var epModelExpanded by remember { mutableStateOf(false) }
    var epModelsError by remember { mutableStateOf<String?>(null) }

    fun doFetchModels() {
        if (epUrl.isBlank() || epApiKey.isBlank()) return
        epFetchingModels = true
        epModelsError = null
        scope.launch {
            val result = fetchModels(epUrl.trimEnd('/'), epApiKey.trim())
            if (result.error == null && result.models.isNotEmpty()) {
                epModels = result.models
                if (epModel !in result.models) epModel = result.models.first()
                epModelsError = null
            } else {
                epModels = emptyList()
                epModelsError = result.error ?: "No models returned"
            }
            epFetchingModels = false
        }
    }

    JetPrefAlertDialog(
        title = if (isEdit) editTitle else addTitle,
        confirmLabel = stringRes(R.string.settings__voice__save),
        onConfirm = {
            val id = existingEndpoint?.id ?: java.util.UUID.randomUUID().toString()
            onSave(
                SavedEndpoint(
                    id = id,
                    name = epName.trim(),
                    baseUrl = epUrl.trimEnd('/'),
                    apiKey = epApiKey.trim(),
                    model = epModel.trim(),
                    presetId = existingEndpoint?.presetId ?: preset?.id ?: "",
                )
            )
            onDismiss()
        },
        dismissLabel = stringRes(R.string.settings__voice__cancel),
        onDismiss = onDismiss,
        confirmEnabled = epName.isNotBlank() && epUrl.isNotBlank() &&
            (epApiKey.isNotBlank() || preset?.requiresApiKey == false),
    ) {
        Column {
            if (!isEdit && preset != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(preset.iconRes),
                        contentDescription = "${preset.name} logo",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (preset.docsUrl != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.settings__voice__docs_link),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                context.launchUrl(preset.docsUrl)
                            },
                        )
                    }
                }
            }

            Text(
                stringRes(R.string.settings__voice__endpoint_name),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            JetPrefTextField(value = epName, onValueChange = { epName = it })
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringRes(R.string.settings__voice__endpoint_base_url),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            JetPrefTextField(value = epUrl, onValueChange = { epUrl = it })
            Spacer(modifier = Modifier.height(8.dp))

            if (preset?.requiresApiKey != false) {
                Text(
                    stringRes(R.string.settings__voice__endpoint_api_key),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                JetPrefTextField(value = epApiKey, onValueChange = { epApiKey = it })
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    stringResource(R.string.settings__voice__no_api_key_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringRes(R.string.settings__voice__model),
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedButton(
                    onClick = { doFetchModels() },
                    enabled = epUrl.isNotBlank() &&
                        (epApiKey.isNotBlank() || preset?.requiresApiKey == false) &&
                        !epFetchingModels,
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
                        Text(epModel.ifBlank { "Select model\u2026" })
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
                AnimatedVisibility(visible = epModelsError != null) {
                    Text(
                        text = epModelsError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    epValidating = true
                    epValidationResult = null
                    scope.launch {
                        epValidationResult = validateEndpoint(
                            epUrl.trimEnd('/'), epApiKey.trim()
                        )
                        epValidating = false
                    }
                },
                enabled = epUrl.isNotBlank() &&
                    (epApiKey.isNotBlank() || preset?.requiresApiKey == false) &&
                    !epValidating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (epValidating) stringRes(R.string.settings__voice__validating)
                    else stringRes(R.string.settings__voice__validate_endpoint),
                )
            }

            AnimatedVisibility(
                visible = epValidationResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                epValidationResult?.let { result ->
                    Text(
                        text = if (result.isSuccess)
                            stringRes(R.string.settings__voice__connection_successful)
                        else
                            result.errorMessage ?: stringRes(R.string.settings__voice__validation_failed),
                        color = if (result.isSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    name: String,
    titleRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JetPrefAlertDialog(
        title = stringResource(titleRes),
        confirmLabel = stringRes(R.string.settings__voice__delete),
        onConfirm = onConfirm,
        dismissLabel = stringRes(R.string.settings__voice__cancel),
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.settings__voice__delete_confirm, name))
    }
}

private fun RefinementStyle.shortDescription(): String = when (this) {
    RefinementStyle.CLEAN_UP -> "Remove filler words and fix grammar"
    RefinementStyle.CASUAL -> "Relaxed, conversational tone"
    RefinementStyle.FORMAL -> "Professional and polished"
    RefinementStyle.PROFESSIONAL -> "Business-appropriate language"
    RefinementStyle.ACADEMIC -> "Scholarly tone, precise vocabulary"
    RefinementStyle.CONCISE -> "Shorten while keeping key meaning"
    RefinementStyle.BULLET_POINTS -> "Convert to organised bullet points"
    RefinementStyle.AGENT -> "Generate content from voice instructions"
    RefinementStyle.CUSTOM -> "Your own custom system prompt"
}
