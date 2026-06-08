/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.setup

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.voice.ModelsResult
import dev.patrickgold.florisboard.ime.voice.ProviderPreset
import dev.patrickgold.florisboard.ime.voice.STT_PRESETS
import dev.patrickgold.florisboard.ime.voice.SavedEndpoint
import dev.patrickgold.florisboard.ime.voice.ValidationResult
import dev.patrickgold.florisboard.ime.voice.WhisperApiClient
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreenScope
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.FlorisStep
import org.florisboard.lib.compose.FlorisStepLayout
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.stringRes

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                if (isGranted) {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.GRANTED)
                } else {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                }
            }
        }

    content(
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
        context,
        navController,
        requestNotification,
        hasNotificationPermission,
        scope,
    )
}

@Composable
private fun FlorisScreenScope.content(
    isFlorisBoardEnabled: Boolean,
    isFlorisBoardSelected: Boolean,
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    hasNotificationPermission: NotificationPermissionState,
    scope: CoroutineScope,
) {

    val stepState = rememberSaveable(saver = FlorisStepState.Saver) {
        val initStep = when {
            !isFlorisBoardEnabled -> Steps.EnableIme.id
            !isFlorisBoardSelected -> Steps.SelectIme.id
            hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
            else -> Steps.VoiceInput.id
        }
        FlorisStepState.new(init = initStep)
    }

    content {
        LaunchedEffect(isFlorisBoardEnabled, isFlorisBoardSelected, hasNotificationPermission) {
            stepState.setCurrentAuto(
                when {
                    !isFlorisBoardEnabled -> Steps.EnableIme.id
                    !isFlorisBoardSelected -> Steps.SelectIme.id
                    hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
                    else -> Steps.VoiceInput.id
                }
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(200L)
                val isEnabled = InputMethodUtils.isFlorisboardEnabled(context)
                if (stepState.getCurrentAuto().value == Steps.EnableIme.id &&
                    stepState.getCurrentManual().value == -1 &&
                    !isFlorisBoardEnabled &&
                    !isFlorisBoardSelected &&
                    hasNotificationPermission == NotificationPermissionState.NOT_SET &&
                    isEnabled
                ) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
            }
        }
        FlorisStepLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            stepState = stepState,
            header = {
                StepText(stringRes(R.string.setup__intro_message))
                Spacer(modifier = Modifier.height(16.dp))
            },
            steps = steps(
                context, navController, requestNotification, scope, stepState
            ),
            footer = {
                footer(context)
            },
        )
    }
}

@Composable
private fun footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy))
        }
        FlorisBulletSpacer()
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository))
        }
    }
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    scope: CoroutineScope,
    stepState: FlorisStepState,
): List<FlorisStep> {

    return listOfNotNull(
        FlorisStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        FlorisStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        if (AndroidVersion.ATLEAST_API33_T) {
            FlorisStep(
                id = Steps.SelectNotification.id,
                title = stringRes(R.string.setup__grant_notification_permission__title),
            ) {
                StepText(stringRes(R.string.setup__grant_notification_permission__description))
                StepButton(stringRes(R.string.setup__grant_notification_permission__btn)) {
                    requestNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else null,
        FlorisStep(
            id = Steps.VoiceInput.id,
            title = stringRes(R.string.settings__voice__setup__voice_title),
        ) {
            StepText(stringRes(R.string.settings__voice__setup__voice_description))

            var selectedPreset by remember { mutableStateOf<ProviderPreset?>(null) }
            var apiKey by remember { mutableStateOf("") }
            var isValidating by remember { mutableStateOf(false) }
            var validationResult by remember { mutableStateOf<ValidationResult?>(null) }

            // Custom provider dialog states
            var showCustomDialog by remember { mutableStateOf(false) }
            var customName by remember { mutableStateOf("Custom Provider") }
            var customUrl by remember { mutableStateOf("") }
            var customApiKey by remember { mutableStateOf("") }
            var customModel by remember { mutableStateOf("whisper-1") }
            var isValidatingCustom by remember { mutableStateOf(false) }
            var customValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
            var customModels by remember { mutableStateOf<List<String>>(emptyList()) }
            var isFetchingModels by remember { mutableStateOf(false) }
            var customModelError by remember { mutableStateOf<String?>(null) }
            var modelDropdownExpanded by remember { mutableStateOf(false) }

            if (selectedPreset == null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    STT_PRESETS.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPreset = preset }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(preset.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    preset.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Custom provider option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Custom provider",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Add your own STT endpoint",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Custom provider dialog
                if (showCustomDialog) {
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("Add Custom Provider") },
                        text = {
                            Column {
                                Text("Name:", style = MaterialTheme.typography.bodySmall)
                                JetPrefTextField(value = customName, onValueChange = { customName = it })
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Base URL:", style = MaterialTheme.typography.bodySmall)
                                JetPrefTextField(
                                    value = customUrl,
                                    onValueChange = {
                                        customUrl = it
                                        customValidationResult = null
                                    },
                                    placeholder = "https://api.example.com"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("API Key:", style = MaterialTheme.typography.bodySmall)
                                JetPrefTextField(
                                    value = customApiKey,
                                    onValueChange = {
                                        customApiKey = it
                                        customValidationResult = null
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Model:", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(
                                        onClick = {
                                            isFetchingModels = true
                                            customModelError = null
                                            scope.launch {
                                                val result = WhisperApiClient(
                                                    customUrl.trimEnd('/'),
                                                    customApiKey.trim()
                                                ).fetchModels()
                                                isFetchingModels = false
                                                if (result.error == null && result.models.isNotEmpty()) {
                                                    customModels = result.models
                                                    if (customModel !in result.models) {
                                                        customModel = result.models.first()
                                                    }
                                                    customModelError = null
                                                } else {
                                                    customModels = emptyList()
                                                    customModelError = result.error ?: "No models found"
                                                }
                                            }
                                        },
                                        enabled = customUrl.isNotBlank() &&
                                            customApiKey.isNotBlank() && !isFetchingModels,
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            if (isFetchingModels) "Fetching..."
                                            else "Fetch models",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (customModels.isNotEmpty()) {
                                    Box {
                                        OutlinedButton(
                                            onClick = { modelDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(customModel.ifBlank { "Select model…" })
                                        }
                                        DropdownMenu(
                                            expanded = modelDropdownExpanded,
                                            onDismissRequest = { modelDropdownExpanded = false },
                                        ) {
                                            customModels.forEach { modelId ->
                                                DropdownMenuItem(
                                                    text = { Text(modelId, maxLines = 1) },
                                                    onClick = {
                                                        customModel = modelId
                                                        modelDropdownExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "${customModels.size} models available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                } else {
                                    JetPrefTextField(value = customModel, onValueChange = { customModel = it })
                                    customModelError?.let { error ->
                                        Text(
                                            error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }

                                // Show validation result
                                customValidationResult?.let { result ->
                                    Text(
                                        text = if (result.isSuccess)
                                            "✓ Connection successful"
                                        else
                                            "✗ ${result.errorMessage ?: "Validation failed"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (result.isSuccess)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isValidatingCustom = true
                                    customValidationResult = null
                                    scope.launch {
                                        customValidationResult = WhisperApiClient(
                                            customUrl.trimEnd('/'),
                                            customApiKey.trim()
                                        ).validateApiKey()
                                        isValidatingCustom = false
                                        if (customValidationResult?.isSuccess == true) {
                                            val endpoint = SavedEndpoint(
                                                id = java.util.UUID.randomUUID().toString(),
                                                name = customName.trim(),
                                                baseUrl = customUrl.trimEnd('/'),
                                                apiKey = customApiKey.trim(),
                                                model = customModel.trim(),
                                                presetId = "",
                                            )
                                            val current = SavedEndpoint.deserializeList(
                                                this@steps.prefs.voice.savedEndpoints.get()
                                            )
                                            this@steps.prefs.voice.savedEndpoints.set(
                                                SavedEndpoint.serializeList(current + endpoint)
                                            )
                                            this@steps.prefs.voice.activeEndpointId.set(endpoint.id)
                                            this@steps.prefs.voice.isVoiceSetUp.set(true)
                                            showCustomDialog = false
                                            stepState.setCurrentAuto(Steps.FinishUp.id)
                                        }
                                    }
                                },
                                enabled = !isValidatingCustom && customName.isNotBlank() &&
                                    customUrl.isNotBlank() && customApiKey.isNotBlank(),
                            ) {
                                Text(if (isValidatingCustom) "Validating..." else "Save & Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } else {
                val preset = selectedPreset!!
                Text(
                    "Enter your ${preset.name} API key:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                JetPrefTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        validationResult = null
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (preset.docsUrl != null) {
                    Text(
                        "Get one at ${preset.docsUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Show validation result if available
                validationResult?.let { result ->
                    Text(
                        text = if (result.isSuccess)
                            "✓ API key validated successfully"
                        else
                            "✗ Invalid: ${result.errorMessage ?: "Validation failed"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.isSuccess)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    TextButton(onClick = { selectedPreset = null }) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (!preset.requiresApiKey) {
                                // Skip validation for providers that don't require API key (e.g., Ollama)
                                val endpoint = SavedEndpoint(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = preset.name,
                                    baseUrl = preset.baseUrl,
                                    apiKey = "",
                                    model = preset.defaultModel,
                                    presetId = preset.id,
                                )
                                val current = SavedEndpoint.deserializeList(
                                    this@steps.prefs.voice.savedEndpoints.get()
                                )
                                scope.launch {
                                    this@steps.prefs.voice.savedEndpoints.set(
                                        SavedEndpoint.serializeList(current + endpoint)
                                    )
                                    this@steps.prefs.voice.activeEndpointId.set(endpoint.id)
                                    this@steps.prefs.voice.isVoiceSetUp.set(true)
                                }
                                stepState.setCurrentAuto(Steps.FinishUp.id)
                            } else {
                                // Validate API key first
                                isValidating = true
                                validationResult = null
                                scope.launch {
                                    validationResult = WhisperApiClient(preset.baseUrl, apiKey.trim()).validateApiKey()
                                    isValidating = false
                                    if (validationResult?.isSuccess == true) {
                                        val endpoint = SavedEndpoint(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = preset.name,
                                            baseUrl = preset.baseUrl,
                                            apiKey = apiKey.trim(),
                                            model = preset.defaultModel,
                                            presetId = preset.id,
                                        )
                                        val current = SavedEndpoint.deserializeList(
                                            this@steps.prefs.voice.savedEndpoints.get()
                                        )
                                        this@steps.prefs.voice.savedEndpoints.set(
                                            SavedEndpoint.serializeList(current + endpoint)
                                        )
                                        this@steps.prefs.voice.activeEndpointId.set(endpoint.id)
                                        this@steps.prefs.voice.isVoiceSetUp.set(true)
                                        stepState.setCurrentAuto(Steps.FinishUp.id)
                                    }
                                }
                            }
                        },
                        enabled = !isValidating && (apiKey.isNotBlank() || !preset.requiresApiKey),
                    ) {
                        Text(if (isValidating) "Validating..." else "Save & Continue")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    scope.launch { this@steps.prefs.voice.isVoiceSetUp.set(false) }
                    stepState.setCurrentAuto(Steps.FinishUp.id)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringRes(R.string.settings__voice__setup__voice_skip))
            }
        },
        FlorisStep(
            id = Steps.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
        ) {
            StepText(stringRes(R.string.setup__finish_up__description_p1))
            StepText(stringRes(R.string.setup__finish_up__description_p2))
            StepButton(label = stringRes(R.string.setup__finish_up__finish_btn)) {
                scope.launch { this@steps.prefs.internal.isImeSetUp.set(true) }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            }
        }
    )
}

private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object SelectNotification : Steps(id = 3)
    data object VoiceInput : Steps(id = 4)
    data object FinishUp : Steps(id = 5)
}
