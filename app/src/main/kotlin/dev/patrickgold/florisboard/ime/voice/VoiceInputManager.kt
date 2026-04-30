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
    SUCCESS,
    ERROR,
}

data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.IDLE,
    val transcribedText: String = "",
    val errorMessage: String = "",
    val amplitude: Float = 0f,
)

class VoiceInputManager(context: Context) {
    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState

    private var audioRecorder: AudioRecorder? = null

    fun startRecording() {
        val recorder = AudioRecorder()
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
            val client = buildClient()
            val config = TranscriptionConfig(
                model = prefs.voice.model.get(),
                language = prefs.voice.language.get(),
            )
            val result = client.transcribe(audioBytes, config)
            _uiState.value = _uiState.value.copy(
                state = VoiceInputState.SUCCESS,
                transcribedText = result.text,
            )
            if (prefs.voice.autoCommit.get() && result.text.isNotEmpty()) {
                commitText()
            }
        } catch (e: Exception) {
            _uiState.value = VoiceInputUiState(
                state = VoiceInputState.ERROR,
                errorMessage = e.message ?: "Transcription failed",
            )
        }
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

    private fun buildClient(): WhisperApiClient {
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

    fun validateCurrentProvider(onResult: (ValidationResult) -> Unit) {
        scope.launch {
            val client = buildClient()
            val result = client.validateApiKey()
            onResult(result)
        }
    }
}
