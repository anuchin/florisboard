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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.collectIn

enum class VoiceInputState {
    IDLE,
    RECORDING,
    PROCESSING,
    REFINING,
    SUCCESS,
    ERROR,
    PERMISSION_REQUIRED,
}

data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.IDLE,
    val transcribedText: String = "",
    val rawTranscribedText: String = "",
    val refinedText: String = "",
    val isRefined: Boolean = false,
    val errorMessage: String = "",
    val amplitude: Float = 0f,
)

class VoiceInputManager(context: Context) {
    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState

    private var audioRecorder: AudioRecorder? = null

    fun hasRecordAudioPermission(): Boolean {
        return appContext.checkCallingOrSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isRefinementEnabled(): Boolean {
        return prefs.voice.refinementEnabled.get()
    }

    fun startRecording() {
        if (!hasRecordAudioPermission()) {
            _uiState.value = VoiceInputUiState(state = VoiceInputState.PERMISSION_REQUIRED)
            return
        }

        val recorder = AudioRecorder(appContext)
        audioRecorder = recorder
        _uiState.value = VoiceInputUiState(state = VoiceInputState.RECORDING)
        scope.launch {
            try {
                val wavBytes = recorder.record()
                _uiState.value = _uiState.value.copy(state = VoiceInputState.PROCESSING, amplitude = 0f)
                transcribe(wavBytes)
            } catch (e: Exception) {
                _uiState.value = VoiceInputUiState(
                    state = VoiceInputState.ERROR,
                    errorMessage = e.message ?: "Recording failed",
                )
            }
        }
        recorder.amplitude.collectIn(scope) { amp ->
            _uiState.value = _uiState.value.copy(amplitude = amp)
        }
    }

    fun stopRecording() {
        audioRecorder?.stop()
    }

    private suspend fun transcribe(audioBytes: ByteArray) {
        try {
            val client = buildWhisperClient()
            val config = TranscriptionConfig(
                model = getActiveModel(),
                language = prefs.voice.language.get(),
            )
            val result = client.transcribe(audioBytes, config)

            val rawText = result.text
            if (rawText.isNotEmpty() && prefs.voice.refinementEnabled.get() && prefs.voice.refinementAutoRefine.get()) {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.REFINING,
                    rawTranscribedText = rawText,
                )
                refineText(rawText)
            } else {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.SUCCESS,
                    transcribedText = rawText,
                    rawTranscribedText = rawText,
                    isRefined = false,
                )
                if (prefs.voice.autoCommit.get() && rawText.isNotEmpty()) {
                    commitText()
                }
            }
        } catch (e: Exception) {
            _uiState.value = VoiceInputUiState(
                state = VoiceInputState.ERROR,
                errorMessage = e.message ?: "Transcription failed",
            )
        }
    }

    fun refineText(text: String? = null) {
        val rawText = text ?: _uiState.value.rawTranscribedText
        if (rawText.isBlank()) return

        val style = prefs.voice.refinementStyle.get()
        val customPrompt = prefs.voice.refinementCustomPrompt.get()

        _uiState.value = _uiState.value.copy(state = VoiceInputState.REFINING)

        scope.launch {
            try {
                val llmClient = buildLlmClient()
                val refined = llmClient.refineText(rawText, style.systemPrompt(customPrompt))
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.SUCCESS,
                    transcribedText = refined,
                    rawTranscribedText = rawText,
                    refinedText = refined,
                    isRefined = true,
                )
            } catch (e: Exception) {
                // If refinement fails, just show the raw text
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.SUCCESS,
                    transcribedText = rawText,
                    rawTranscribedText = rawText,
                    isRefined = false,
                    errorMessage = "Refinement failed: ${e.message}",
                )
            }
        }
    }

    fun toggleRefined() {
        val current = _uiState.value
        if (current.rawTranscribedText.isBlank() || current.refinedText.isBlank()) return
        val showingRefined = current.isRefined
        _uiState.value = current.copy(
            transcribedText = if (showingRefined) current.rawTranscribedText else current.refinedText,
            isRefined = !showingRefined,
        )
    }

    fun commitText() {
        val text = _uiState.value.transcribedText
        if (text.isNotEmpty()) {
            editorInstance.commitText(text)
        }
        reset()
    }

    fun reset() {
        _uiState.value = VoiceInputUiState()
    }

    private fun buildWhisperClient(): WhisperApiClient {
        val activeId = prefs.voice.activeEndpointId.get()
        if (activeId.isNotBlank()) {
            val endpoints = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
            val active = endpoints.find { it.id == activeId }
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

    private fun buildLlmClient(): LlmApiClient {
        val activeId = prefs.voice.llmActiveEndpointId.get()
        if (activeId.isNotBlank()) {
            val endpoints = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
            val active = endpoints.find { it.id == activeId }
            if (active != null) {
                return LlmApiClient(
                    baseUrl = active.baseUrl.trimEnd('/'),
                    apiKey = active.apiKey,
                    model = active.model,
                )
            }
        }
        // Fall back to using the same Whisper endpoint with a default model
        val whisperActiveId = prefs.voice.activeEndpointId.get()
        if (whisperActiveId.isNotBlank()) {
            val endpoints = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
            val active = endpoints.find { it.id == whisperActiveId }
            if (active != null) {
                return LlmApiClient(
                    baseUrl = active.baseUrl.trimEnd('/'),
                    apiKey = active.apiKey,
                    model = "gpt-4o-mini",
                )
            }
        }
        val provider = prefs.voice.provider.get()
        return when (provider) {
            VoiceProvider.OPENAI -> LlmApiClient(
                baseUrl = "https://api.openai.com",
                apiKey = prefs.voice.openaiApiKey.get(),
                model = "gpt-4o-mini",
            )
            VoiceProvider.GROQ -> LlmApiClient(
                baseUrl = "https://api.groq.com/openai",
                apiKey = prefs.voice.groqApiKey.get(),
                model = "llama-3.1-8b-instant",
            )
            VoiceProvider.CUSTOM -> LlmApiClient(
                baseUrl = prefs.voice.customEndpointUrl.get().trimEnd('/'),
                apiKey = prefs.voice.customApiKey.get(),
                model = prefs.voice.customModel.get(),
            )
        }
    }

    private fun getActiveModel(): String {
        val activeId = prefs.voice.activeEndpointId.get()
        if (activeId.isNotBlank()) {
            val endpoints = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
            val active = endpoints.find { it.id == activeId }
            if (active != null) return active.model
        }
        return prefs.voice.model.get()
    }

    fun validateCurrentProvider(onResult: (ValidationResult) -> Unit) {
        scope.launch {
            val client = buildWhisperClient()
            val result = client.validateApiKey()
            onResult(result)
        }
    }
}
