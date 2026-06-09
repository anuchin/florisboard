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

import android.content.Context
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.editorInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.voxkb.lib.kotlin.collectIn

enum class VoiceInputState {
    IDLE,
    RECORDING,
    PROCESSING,
    REFINING,
    INSERTING,
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
    val isAgentMode: Boolean = false,
    val errorMessage: String = "",
    val amplitude: Float = 0f,
    val amplitudeHistory: List<Float> = List(AMPLITUDE_HISTORY_SIZE) { 0f },
    val durationMs: Long = 0L,
    val providerLabel: String = "",
    val modelLabel: String = "",
    val languageLabel: String = "Auto",
    val done: Boolean = false,
)

const val AMPLITUDE_HISTORY_SIZE = 36

class VoiceInputManager(context: Context) {
    private val prefs by VoxKBPreferenceStore
    private val editorInstance by context.editorInstance()
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState

    private var audioRecorder: AudioRecorder? = null
    private var activeJob: Job? = null
    private var autoInsertJob: Job? = null
    private var recordingStartMs: Long = 0L
    private var durationTickJob: Job? = null

    fun hasRecordAudioPermission(): Boolean {
        return appContext.checkCallingOrSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isRefinementEnabled(): Boolean {
        return prefs.voice.refinementEnabled.get()
    }

    fun toggleRefinement() {
        scope.launch {
            val current = prefs.voice.refinementEnabled.get()
            prefs.voice.refinementEnabled.set(!current)
        }
    }

    fun startRecording() {
        if (!hasRecordAudioPermission()) {
            _uiState.value = _uiState.value.copy(state = VoiceInputState.PERMISSION_REQUIRED)
            return
        }

        // If a job is in flight, cancel it cleanly.
        activeJob?.cancel()
        activeJob = null

        val recorder = AudioRecorder(appContext)
        audioRecorder = recorder
        recordingStartMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            state = VoiceInputState.RECORDING,
            amplitude = 0f,
            amplitudeHistory = List(AMPLITUDE_HISTORY_SIZE) { 0f },
            durationMs = 0L,
            errorMessage = "",
        )
        startDurationTicker()

        activeJob = scope.launch {
            try {
                val wavBytes = recorder.record()
                stopDurationTicker()
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.PROCESSING,
                    amplitude = 0f,
                )
                transcribe(wavBytes)
            } catch (e: CancellationException) {
                stopDurationTicker()
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.IDLE,
                    amplitude = 0f,
                    durationMs = 0L,
                )
            } catch (e: Exception) {
                stopDurationTicker()
                _uiState.value = VoiceInputUiState(
                    state = VoiceInputState.ERROR,
                    errorMessage = sanitizeError(e),
                )
            }
        }

        recorder.amplitude.collectIn(scope) { amp ->
            _uiState.value = _uiState.value.copy(amplitude = amp)
        }
        recorder.amplitudeHistory.collectIn(scope) { history ->
            _uiState.value = _uiState.value.copy(amplitudeHistory = history)
        }
    }

    fun stopRecording() {
        audioRecorder?.stop()
    }

    /**
     * Cancels the current operation. Safe to call from any state.
     * - If recording: stops the recorder and discards audio.
     * - If processing / refining: cancels the in-flight API call.
     * - In other states: returns the UI to IDLE.
     */
    fun cancel() {
        when (_uiState.value.state) {
            VoiceInputState.RECORDING -> {
                audioRecorder?.stop()
                activeJob?.cancel()
                stopDurationTicker()
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.IDLE,
                    amplitude = 0f,
                    durationMs = 0L,
                )
            }
            VoiceInputState.PROCESSING,
            VoiceInputState.REFINING -> {
                activeJob?.cancel()
                stopDurationTicker()
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.IDLE,
                    amplitude = 0f,
                    durationMs = 0L,
                )
            }
            else -> reset()
        }
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
            val style = prefs.voice.refinementStyle.get()
            val isAgent = style.isAgent
            val shouldReview = prefs.voice.reviewBeforeInsert.get()

            if (rawText.isEmpty()) return

            if (prefs.voice.refinementEnabled.get()) {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.REFINING,
                    rawTranscribedText = rawText,
                    isAgentMode = isAgent,
                )
                refineText(rawText, autoInsert = !shouldReview)
            } else if (shouldReview) {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.SUCCESS,
                    transcribedText = rawText,
                    rawTranscribedText = rawText,
                    isRefined = false,
                    isAgentMode = isAgent,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.INSERTING,
                    transcribedText = rawText,
                    rawTranscribedText = rawText,
                    isRefined = false,
                    isAgentMode = isAgent,
                    done = false,
                )
                scope.launch {
                    delay(400)
                    val text = _uiState.value.transcribedText
                    if (text.isNotEmpty()) {
                        val toCommit = if (!text.endsWith(' ') && !text.endsWith('\n')) "$text " else text
                        editorInstance.commitText(toCommit)
                    }
                    _uiState.value = _uiState.value.copy(done = true)
                    delay(700)
                    reset()
                }.also { autoInsertJob = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = VoiceInputUiState(
                state = VoiceInputState.ERROR,
                errorMessage = sanitizeError(e),
            )
        }
    }

    fun refineText(text: String? = null, autoInsert: Boolean = true) {
        val rawText = text ?: _uiState.value.rawTranscribedText
        if (rawText.isBlank()) return

        val style = prefs.voice.refinementStyle.get()
        val customPrompt = prefs.voice.refinementCustomPrompt.get()
        val tonePrompt = prefs.voice.toneStyle.get().systemPrompt()
        val effectivePrompt = when {
            style == RefinementStyle.CUSTOM && customPrompt.isNotBlank() -> customPrompt
            style == RefinementStyle.CUSTOM -> customPrompt.ifBlank { tonePrompt }
            customPrompt.isNotBlank() -> customPrompt
            else -> tonePrompt
        }

        val isAgent = style.isAgent
        _uiState.value = _uiState.value.copy(
            state = VoiceInputState.REFINING,
            isAgentMode = isAgent,
        )

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val llmClient = buildLlmClient()
                val refined = llmClient.refineText(rawText, effectivePrompt)
                if (autoInsert) {
                    _uiState.value = _uiState.value.copy(
                        state = VoiceInputState.INSERTING,
                        transcribedText = refined,
                        rawTranscribedText = rawText,
                        refinedText = refined,
                        isRefined = true,
                        isAgentMode = isAgent,
                        done = false,
                    )
                    delay(400)
                    val toCommit = refined
                    if (toCommit.isNotEmpty()) {
                        val text2 = if (!toCommit.endsWith(' ') && !toCommit.endsWith('\n')) "$toCommit " else toCommit
                        editorInstance.commitText(text2)
                    }
                    _uiState.value = _uiState.value.copy(done = true)
                    delay(700)
                    reset()
                } else {
                    _uiState.value = _uiState.value.copy(
                        state = VoiceInputState.SUCCESS,
                        transcribedText = refined,
                        rawTranscribedText = rawText,
                        refinedText = refined,
                        isRefined = true,
                        isAgentMode = isAgent,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.ERROR,
                    transcribedText = rawText,
                    rawTranscribedText = rawText,
                    isRefined = false,
                    isAgentMode = isAgent,
                    errorMessage = sanitizeError(e),
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

    fun updateTranscript(text: String) {
        _uiState.value = _uiState.value.copy(transcribedText = text)
    }

    fun clearLastTranscription() {
        val current = _uiState.value
        if (current.state == VoiceInputState.SUCCESS && current.transcribedText.isNotBlank()) {
            _uiState.value = current.copy(
                transcribedText = "",
                rawTranscribedText = "",
                refinedText = "",
                isRefined = false,
                state = VoiceInputState.IDLE,
            )
        }
    }

    fun commitText(appendSpace: Boolean = true) {
        val raw = _uiState.value.transcribedText
        if (raw.isNotEmpty()) {
            val text = if (appendSpace && !raw.endsWith(' ') && !raw.endsWith('\n')) "$raw " else raw
            editorInstance.commitText(text)
        }
        reset()
    }

    fun reset() {
        stopDurationTicker()
        activeJob?.cancel()
        activeJob = null
        autoInsertJob?.cancel()
        autoInsertJob = null
        audioRecorder?.clearHistory()
        _uiState.value = VoiceInputUiState()
    }

    private fun startDurationTicker() {
        stopDurationTicker()
        durationTickJob = scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - recordingStartMs
                _uiState.value = _uiState.value.copy(durationMs = elapsed)
                delay(100)
            }
        }
    }

    private fun stopDurationTicker() {
        durationTickJob?.cancel()
        durationTickJob = null
    }

    private fun findActiveSttEndpoint(): SavedEndpoint? {
        val activeId = prefs.voice.activeEndpointId.get()
        if (activeId.isBlank()) return null
        val endpoints = SavedEndpoint.deserializeList(prefs.voice.savedEndpoints.get())
        return endpoints.find { it.id == activeId }
    }

    private fun findActiveLlmEndpoint(): SavedEndpoint? {
        val activeId = prefs.voice.llmActiveEndpointId.get()
        if (activeId.isBlank()) return null
        val endpoints = SavedEndpoint.deserializeList(prefs.voice.llmSavedEndpoints.get())
        return endpoints.find { it.id == activeId }
    }

    private fun buildWhisperClient(): WhisperApiClient {
        val active = findActiveSttEndpoint()
            ?: throw IllegalStateException("No active STT endpoint configured. Open Settings → Voice.")
        return WhisperApiClient(
            baseUrl = active.baseUrl.trimEnd('/'),
            apiKey = active.apiKey,
        )
    }

    private fun buildLlmClient(): LlmApiClient {
        val active = findActiveLlmEndpoint()
            ?: throw IllegalStateException("No active LLM endpoint configured. Open Settings → Voice.")
        return LlmApiClient(
            baseUrl = active.baseUrl.trimEnd('/'),
            apiKey = active.apiKey,
            model = active.model,
        )
    }

    private fun getActiveModel(): String {
        return findActiveSttEndpoint()?.model?.ifBlank { "whisper-1" } ?: "whisper-1"
    }

    fun validateCurrentProvider(onResult: (ValidationResult) -> Unit) {
        scope.launch {
            val client = buildWhisperClient()
            val result = client.validateApiKey()
            onResult(result)
        }
    }

    /**
     * Returns provider/model/language info for the current configuration, suitable
     * for display in the UI header chip.
     */
    fun snapshotProviderInfo(): Triple<String, String, String> {
        val active = findActiveSttEndpoint()
        if (active != null) {
            return Triple(
                active.name.ifBlank { "Custom" },
                active.model.ifBlank { "whisper-1" },
                prefs.voice.language.get().ifBlank { "Auto" },
            )
        }
        return Triple("Not configured", "", "Auto")
    }
}

/**
 * Maps thrown exceptions to user-friendly messages, with API keys redacted to
 * avoid leaking secrets through the IME UI.
 */
internal fun sanitizeError(t: Throwable): String {
    val raw = t.message ?: t::class.java.simpleName.ifBlank { "Unknown error" }
    val keyPatterns = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
        Regex("""sk-proj-[A-Za-z0-9_\-]{16,}"""),
        Regex("""gsk_[A-Za-z0-9_\-]{16,}"""),
        Regex("""Bearer\s+[A-Za-z0-9_\-.]{16,}"""),
    )
    var cleaned = raw
    keyPatterns.forEach { cleaned = cleaned.replace(it, "[REDACTED]") }

    val lc = cleaned.lowercase()
    return when {
        lc.contains("401") || lc.contains("incorrect api key") || lc.contains("invalid_api_key") ->
            "Invalid API key. Check Settings → Voice."
        lc.contains("403") || lc.contains("forbidden") ->
            "Access denied. Check your API permissions."
        lc.contains("429") || lc.contains("rate limit") ->
            "Rate limit reached. Try again in a moment."
        lc.contains("enotfound") || lc.contains("unable to resolve host") || lc.contains("no address associated") ->
            "No internet connection."
        lc.contains("etimedout") || lc.contains("timeout") || lc.contains("timed out") ->
            "Connection timed out."
        lc.contains("network") || lc.contains("failed to connect") ->
            "Network error. Check your connection."
        (lc.contains("no active") && lc.contains("endpoint")) || lc.contains("missing") ->
            "Voice input isn't configured. Open Settings → Voice."
        else -> cleaned.take(140)
    }
}
