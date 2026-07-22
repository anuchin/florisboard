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
import com.voxkb.ime.editor.OperationUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VoiceInputState {
    IDLE,
    RECORDING,
    PROCESSING,
    REFINING,
    REVIEW,
    INSERTING,
    ERROR,
    PERMISSION_REQUIRED,
    SETUP_REQUIRED,
}

data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.IDLE,
    val transcribedText: String = "",
    val rawTranscribedText: String = "",
    val refinedText: String = "",
    val isRefined: Boolean = false,
    val isAgentMode: Boolean = false,
    val reviewText: String = "",
    val errorMessage: String = "",
    val amplitude: Float = 0f,
    val amplitudeHistory: List<Float> = List(AMPLITUDE_HISTORY_SIZE) { 0f },
    val durationMs: Long = 0L,
    val providerLabel: String = "",
    val modelLabel: String = "",
    val languageLabel: String = "Auto",
    val done: Boolean = false,
    /** Whether the last auto-insert can be undone (i.e. text is currently in the editor). */
    val canUndo: Boolean = false,
    /** Whether a previously undone insert can be redone. */
    val canRedo: Boolean = false,
    /** Shown in SETUP_REQUIRED: human-readable reason. */
    val setupMessage: String = "",
    /** Whether the user can fall back to raw transcription (STT ok, only LLM missing). */
    val canUseTranscribeOnly: Boolean = false,
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

    /**
     * One-level local undo stack for the last auto-inserted dictated text.
     * - [lastInsertedText] holds the exact string that was committed.
     * - [lastInsertUndone] flips to true after [undoLastInsert], enabling [redoInsert].
     * Reset whenever a new recording starts.
     */
    private var lastInsertedText: String = ""
    private var lastInsertUndone: Boolean = false

    /** Active coroutine jobs collecting recorder amplitude flows. */
    private val amplitudeJobs = mutableListOf<Job>()

    /** Absolute editor positions of the last auto-inserted text, used for safe undo. */
    private var insertionStart: Int = -1
    private var insertionEnd: Int = -1

    fun hasRecordAudioPermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
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

    fun destroy() {
        stopDurationTicker()
        activeJob?.cancel()
        autoInsertJob?.cancel()
        amplitudeJobs.forEach { it.cancel() }
        audioRecorder?.stop()
        scope.cancel()
    }

    fun startRecording() {
        if (!hasRecordAudioPermission()) {
            _uiState.value = _uiState.value.copy(state = VoiceInputState.PERMISSION_REQUIRED)
            return
        }

        // Up-front configuration gate: never record audio that can't be processed.
        // Surface exactly what's missing so the user can fix it before dictating.
        val sttReady = findActiveSttEndpoint() != null
        val refinementOn = prefs.voice.refinementEnabled.get()
        val llmReady = !refinementOn || findActiveLlmEndpoint() != null
        if (!sttReady || !llmReady) {
            _uiState.value = _uiState.value.copy(
                state = VoiceInputState.SETUP_REQUIRED,
                setupMessage = if (!sttReady) {
                    "Add a speech-to-text provider to use voice typing."
                } else {
                    "AI refinement is on, but no AI model is configured."
                },
                canUseTranscribeOnly = sttReady,
            )
            return
        }

        // Starting a fresh dictation invalidates any previous insert's undo/redo state.
        lastInsertedText = ""
        lastInsertUndone = false

        // If a job is in flight, cancel it cleanly.
        activeJob?.cancel()
        activeJob = null
        amplitudeJobs.forEach { it.cancel() }
        amplitudeJobs.clear()

        val (provider, model, language) = snapshotProviderInfo()
        val recorder = AudioRecorder(appContext)
        audioRecorder = recorder
        recordingStartMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            state = VoiceInputState.RECORDING,
            amplitude = 0f,
            amplitudeHistory = List(AMPLITUDE_HISTORY_SIZE) { 0f },
            durationMs = 0L,
            errorMessage = "",
            providerLabel = provider,
            modelLabel = model,
            languageLabel = language,
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
                amplitudeJobs.forEach { it.cancel() }
                amplitudeJobs.clear()
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.IDLE,
                    amplitude = 0f,
                    durationMs = 0L,
                )
            } catch (e: Exception) {
                stopDurationTicker()
                amplitudeJobs.forEach { it.cancel() }
                amplitudeJobs.clear()
                _uiState.value = VoiceInputUiState(
                    state = VoiceInputState.ERROR,
                    errorMessage = sanitizeError(e),
                )
            }
        }

        amplitudeJobs.clear()
        amplitudeJobs.add(scope.launch {
            recorder.amplitude.collect { amp -> _uiState.value = _uiState.value.copy(amplitude = amp) }
        })
        amplitudeJobs.add(scope.launch {
            recorder.amplitudeHistory.collect { history ->
                _uiState.value = _uiState.value.copy(amplitudeHistory = history)
            }
        })
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
                amplitudeJobs.forEach { it.cancel() }
                amplitudeJobs.clear()
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

            if (rawText.isEmpty()) {
                // Nothing recognizable — return to idle without inserting anything.
                _uiState.value = VoiceInputUiState()
                return
            }

            if (prefs.voice.refinementEnabled.get()) {
                _uiState.value = _uiState.value.copy(
                    state = VoiceInputState.REFINING,
                    rawTranscribedText = rawText,
                    isAgentMode = isAgent,
                )
                refineText(rawText)
            } else {
                autoInsert(rawText, rawText, isRefined = false, isAgent = isAgent)
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

    /**
     * Commits [displayText] (the polished text, or the raw transcript when refinement is off)
     * into the editor and stages it for one-level undo. [rawText] is retained for diagnostics.
     */
    private fun autoInsert(
        displayText: String,
        rawText: String,
        isRefined: Boolean,
        isAgent: Boolean,
        forceCommit: Boolean = false,
    ) {
        val shouldReview = !forceCommit &&
            (prefs.voice.reviewBeforeInsert.get() || !prefs.voice.autoCommit.get())
        if (shouldReview) {
            _uiState.value = _uiState.value.copy(
                state = VoiceInputState.REVIEW,
                transcribedText = displayText,
                rawTranscribedText = rawText,
                refinedText = if (isRefined) displayText else "",
                isRefined = isRefined,
                isAgentMode = isAgent,
                reviewText = displayText,
                done = false,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            state = VoiceInputState.INSERTING,
            transcribedText = displayText,
            rawTranscribedText = rawText,
            refinedText = if (isRefined) displayText else "",
            isRefined = isRefined,
            isAgentMode = isAgent,
            done = false,
        )
        autoInsertJob = scope.launch {
            val toCommit = withTrailingSpace(displayText)
            if (toCommit.isNotEmpty()) {
                editorInstance.commitText(toCommit)
                val selEnd = editorInstance.activeContent.selection.end
                insertionStart = selEnd - toCommit.length
                insertionEnd = selEnd
                lastInsertedText = toCommit
                lastInsertUndone = false
            }
            _uiState.value = _uiState.value.copy(done = true, canUndo = toCommit.isNotEmpty(), canRedo = false)
            delay(700)
            // Return to idle but preserve the undo/redo flags so the bottom bar stays usable.
            _uiState.value = _uiState.value.copy(
                state = VoiceInputState.IDLE,
                amplitude = 0f,
                durationMs = 0L,
            )
        }
    }


    fun commitReview() {
        val text = _uiState.value.reviewText
        if (text.isBlank()) { reset(); return }
        autoInsert(
            displayText = text,
            rawText = _uiState.value.rawTranscribedText,
            isRefined = _uiState.value.isRefined,
            isAgent = _uiState.value.isAgentMode,
            forceCommit = true,
        )
    }

    fun discardReview() {
        reset()
    }

    /**
     * Recovery from SETUP_REQUIRED when only the AI model is missing: turns off
     * refinement and returns to idle so the user can dictate immediately.
     */
    fun useTranscribeOnly() {
        scope.launch {
            prefs.voice.refinementEnabled.set(false)
            _uiState.value = VoiceInputUiState()
        }
    }

    private fun withTrailingSpace(text: String): String =
        if (text.isNotEmpty() && !text.endsWith(' ') && !text.endsWith('\n')) "$text " else text

    fun refineText(text: String? = null) {
        val rawText = text ?: _uiState.value.rawTranscribedText
        if (rawText.isBlank()) return

        val style = prefs.voice.refinementStyle.get()
        val customPrompt = prefs.voice.refinementCustomPrompt.get()
        val effectivePrompt = when (style) {
            RefinementStyle.CUSTOM -> customPrompt.ifBlank { style.systemPrompt() }
            else -> style.systemPrompt()
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
                autoInsert(refined, rawText, isRefined = true, isAgent = isAgent)
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

    /**
     * Removes exactly the text that was last auto-inserted (one level of undo).
     * Selects backwards over [lastInsertedText] characters then deletes them, so
     * the user's surrounding typed text is never touched.
     */
    fun undoLastInsert() {
        if (!canUndo()) return
        val text = lastInsertedText
        if (text.isEmpty()) return
        // Verify the text at the recorded range still matches.
        val content = editorInstance.activeContent
        val rangeStart = insertionStart.coerceIn(0, content.text.length)
        val rangeEnd = insertionEnd.coerceIn(rangeStart, content.text.length)
        val actualText = content.text.substring(rangeStart, rangeEnd)
        if (actualText != text) {
            // Text was edited after insert — can't safely undo.
            _uiState.value = _uiState.value.copy(canUndo = false, canRedo = false)
            lastInsertedText = ""
            return
        }
        // Select the range and delete it.
        editorInstance.setSelection(insertionStart, insertionEnd)
        editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
        lastInsertUndone = true
        _uiState.value = _uiState.value.copy(canUndo = false, canRedo = true)
    }

    /** Re-inserts the previously undone dictated text (one level of redo). */
    fun redoInsert() {
        if (!canRedo()) return
        val text = lastInsertedText
        if (text.isEmpty()) return
        editorInstance.commitText(text)
        val selEnd = editorInstance.activeContent.selection.end
        insertionStart = selEnd - text.length
        insertionEnd = selEnd
        lastInsertUndone = false
        _uiState.value = _uiState.value.copy(canUndo = true, canRedo = false)
    }

    /**
     * Recovery path for when AI refinement fails: commits the raw transcript
     * as-is so the user never loses their dictation. Skips review and refinement.
     */
    fun insertRawTranscript() {
        val raw = _uiState.value.rawTranscribedText
        if (raw.isBlank()) { reset(); return }
        autoInsert(raw, raw, isRefined = false, isAgent = false, forceCommit = true)
    }

    fun canUndo(): Boolean = lastInsertedText.isNotEmpty() && !lastInsertUndone

    fun canRedo(): Boolean = lastInsertedText.isNotEmpty() && lastInsertUndone

    fun reset() {
        stopDurationTicker()
        activeJob?.cancel()
        activeJob = null
        autoInsertJob?.cancel()
        autoInsertJob = null
        amplitudeJobs.forEach { it.cancel() }
        amplitudeJobs.clear()
        audioRecorder?.clearHistory()
        lastInsertedText = ""
        lastInsertUndone = false
        insertionStart = -1
        insertionEnd = -1
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
            presetId = active.presetId,
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
