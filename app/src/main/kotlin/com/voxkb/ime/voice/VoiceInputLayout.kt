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
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.voxkb.VoxKBImeService
import com.voxkb.R
import com.voxkb.app.VoxKBAppActivity
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.ime.ImeUiMode
import com.voxkb.ime.keyboard.VoxKBImeSizing
import com.voxkb.ime.text.keyboard.TextKeyData
import com.voxkb.ime.theme.VoxKBImeUi
import com.voxkb.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import com.voxkb.lib.snygg.ui.SnyggBox
import com.voxkb.lib.snygg.ui.SnyggButton
import com.voxkb.lib.snygg.ui.SnyggChip
import com.voxkb.lib.snygg.ui.SnyggColumn
import com.voxkb.lib.snygg.ui.SnyggIcon
import com.voxkb.lib.snygg.ui.SnyggIconButton
import com.voxkb.lib.snygg.ui.SnyggRow
import com.voxkb.lib.snygg.ui.SnyggText
import com.voxkb.lib.snygg.ui.rememberSnyggThemeQuery

private const val STATE_TRANSITION_MS = 320
private const val PULSE_CYCLE_MS = 1800
private const val WAVEFORM_BAR_COUNT = 36

@Composable
fun VoiceInputLayout(modifier: Modifier = Modifier) {
    VoiceInputLayoutContent(modifier)
}

@Composable
private fun VoiceInputLayoutContent(modifier: Modifier) {
    val ctx = LocalContext.current
    val voiceInputManager = remember { VoiceInputManager(ctx) }
    DisposableEffect(voiceInputManager) {
        onDispose { voiceInputManager.destroy() }
    }
    val keyboardManager by ctx.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()
    val prefs by VoxKBPreferenceStore
    val refinementEnabled by prefs.voice.refinementEnabled.collectAsState()
    val refinementStyle by prefs.voice.refinementStyle.collectAsState()

    val openSettings = rememberOpenVoiceSettings()

    val density = LocalDensity.current
    val swipeThreshold = with(density) { -140.dp.toPx() }

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    SnyggBox(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(VoxKBImeSizing.imeUiHeight())
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffsetX < swipeThreshold) {
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(with(density) { -260.dp.toPx() }, 0f)
                }
            }
            .graphicsLayer { translationX = swipeOffsetX },
        contentAlignment = Alignment.Center,
    ) {
        SnyggColumn(
            elementName = VoxKBImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            VoiceTopBar(
                refinementEnabled = refinementEnabled,
                refinementStyle = refinementStyle,
                isRecording = uiState.state == VoiceInputState.RECORDING,
                durationMs = uiState.durationMs,
                onClose = {
                    voiceInputManager.reset()
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VoiceStage(
                    uiState = uiState,
                    manager = voiceInputManager,
                    keyboardManager = keyboardManager,
                    openSettings = openSettings,
                    ctx = ctx,
                )
            }

            VoiceBottomBar(
                keyboardManager = keyboardManager,
                onBackspace = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE) },
                onSpace = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.SPACE) },
                onUndo = { voiceInputManager.undoLastInsert() },
                onRedo = { voiceInputManager.redoInsert() },
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
            )
        }
    }
}

@Composable
private fun VoiceTopBar(
    refinementEnabled: Boolean,
    refinementStyle: RefinementStyle,
    isRecording: Boolean,
    durationMs: Long,
    onClose: () -> Unit,
) {
    val closeDesc = stringResource(R.string.voice__close)
    var showDropdown by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val prefs by VoxKBPreferenceStore

    val timerAlpha by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = tween(300),
        label = "timer-alpha",
    )

    SnyggRow(
        elementName = VoxKBImeUi.VoiceTopBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SnyggChip(
                elementName = VoxKBImeUi.VoiceChip.elementName,
                onClick = { showDropdown = true },
                imageVector = if (refinementEnabled) refinementStyle.modeIcon() else Icons.Filled.Mic,
                text = if (refinementEnabled) refinementStyle.displayName()
                    else stringResource(R.string.voice__mode_transcribe),
            )

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                VoicePanelMode.entries.forEach { mode ->
                    val selected = if (mode.style == null) {
                        !refinementEnabled
                    } else {
                        refinementEnabled && refinementStyle == mode.style
                    }
                    DropdownMenuItem(
                        text = {
                            SnyggText(
                                elementName = VoxKBImeUi.VoiceChipText.elementName,
                                text = mode.displayName(),
                            )
                        },
                        leadingIcon = {
                            SnyggIcon(
                                elementName = VoxKBImeUi.VoiceChip.elementName,
                                imageVector = mode.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (selected) {
                                SnyggIcon(
                                    elementName = VoxKBImeUi.VoiceTopBar.elementName,
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        onClick = {
                            coroutineScope.launch {
                                if (mode.style == null) {
                                    prefs.voice.refinementEnabled.set(false)
                                } else {
                                    prefs.voice.refinementStyle.set(mode.style)
                                    prefs.voice.refinementEnabled.set(true)
                                }
                            }
                            showDropdown = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (timerAlpha > 0.01f) {
            val listeningDesc = stringResource(R.string.voice__listening)
            SnyggText(
                elementName = VoxKBImeUi.VoiceInputRoot.elementName,
                text = formatDuration(durationMs),
                modifier = Modifier
                    .graphicsLayer { alpha = timerAlpha }
                    .semantics {
                        contentDescription = listeningDesc
                    },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceTopBar.elementName,
            modifier = Modifier.size(32.dp),
            onClick = onClose,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceTopBar.elementName,
                imageVector = Icons.Filled.Close,
                contentDescription = closeDesc,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private enum class VoicePanelMode(val style: RefinementStyle?) {
    TRANSCRIBE(null),
    CLEAN_UP(RefinementStyle.CLEAN_UP),
    CASUAL(RefinementStyle.CASUAL),
    FORMAL(RefinementStyle.FORMAL),
    PROFESSIONAL(RefinementStyle.PROFESSIONAL),
    CONCISE(RefinementStyle.CONCISE),
    BULLET_POINTS(RefinementStyle.BULLET_POINTS),
    AGENT(RefinementStyle.AGENT);

    @Composable
    fun displayName(): String = style?.displayName() ?: stringResource(R.string.voice__mode_transcribe)

    fun icon(): ImageVector = style?.modeIcon() ?: Icons.Filled.Mic
}

private fun RefinementStyle.modeIcon(): ImageVector = when (this) {
    RefinementStyle.AGENT -> Icons.Filled.SmartToy
    else -> Icons.Outlined.AutoAwesome
}

@Composable
private fun VoiceStage(
    uiState: VoiceInputUiState,
    manager: VoiceInputManager,
    keyboardManager: com.voxkb.ime.keyboard.KeyboardManager,
    openSettings: () -> Unit,
    ctx: Context,
) {
    AnimatedContent(
        targetState = uiState.state,
        transitionSpec = {
            (fadeIn(tween(STATE_TRANSITION_MS)) +
                slideInHorizontally(tween(STATE_TRANSITION_MS)) { it / 4 } +
                scaleIn(initialScale = 0.96f, animationSpec = tween(STATE_TRANSITION_MS)))
                .togetherWith(
                    fadeOut(tween(STATE_TRANSITION_MS)) +
                        slideOutHorizontally(tween(STATE_TRANSITION_MS)) { it / 4 } +
                        scaleOut(targetScale = 1.04f, animationSpec = tween(STATE_TRANSITION_MS))
                )
        },
        label = "voice-stage",
    ) { state ->
        when (state) {
            VoiceInputState.IDLE,
            VoiceInputState.RECORDING -> RecordingStage(
                state = state,
                amplitudeHistory = uiState.amplitudeHistory,
                onStartRecording = { manager.startRecording() },
                onStopRecording = { manager.stopRecording() },
            )
            VoiceInputState.PROCESSING -> ProcessingStage(
                label = stringResource(R.string.voice__transcribing),
                sublabel = stringResource(
                    R.string.voice__sending_to,
                    uiState.providerLabel.ifBlank { stringResource(R.string.voice__speech_to_text) },
                ),
                onCancel = { manager.cancel() },
            )
            VoiceInputState.REFINING -> ProcessingStage(
                label = if (uiState.isAgentMode) stringResource(R.string.voice__generating)
                        else stringResource(R.string.voice__refining),
                sublabel = if (uiState.isAgentMode) stringResource(R.string.voice__generating_sublabel)
                           else stringResource(R.string.voice__refining_sublabel),
                onCancel = { manager.cancel() },
            )
            VoiceInputState.REVIEW -> ReviewStage(
                uiState = uiState,
                onInsert = { manager.commitReview() },
                onDiscard = { manager.discardReview() },
                onRefine = { manager.refineText() },
            )
            VoiceInputState.INSERTING -> AutoInsertStage(uiState = uiState)
            VoiceInputState.ERROR -> ErrorStage(
                message = uiState.errorMessage.ifBlank { stringResource(R.string.voice__error_title) },
                rawTranscript = uiState.rawTranscribedText,
                onRetry = { manager.startRecording() },
                onDismiss = { manager.reset() },
                onInsertRaw = { manager.insertRawTranscript() },
                onOpenSettings = openSettings,
            )
            VoiceInputState.PERMISSION_REQUIRED -> PermissionStage(
                onRequestPermission = {
                    val intent = Intent(ctx, VoxKBAppActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        action = "REQUEST_RECORD_AUDIO"
                    }
                    ctx.startActivity(intent)
                },
            )
            VoiceInputState.SETUP_REQUIRED -> SetupStage(
                message = uiState.setupMessage,
                canUseTranscribeOnly = uiState.canUseTranscribeOnly,
                onOpenSettings = openSettings,
                onUseTranscribeOnly = { manager.useTranscribeOnly() },
            )
        }
    }
}

@Composable
private fun AutoInsertStage(uiState: VoiceInputUiState) {
    SnyggColumn(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = if (uiState.done) stringResource(R.string.voice__done)
                   else stringResource(R.string.voice__inserting),
        )
        if (uiState.done) {
            Spacer(modifier = Modifier.height(8.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceInputRoot.elementName,
                text = stringResource(R.string.voice__inserted_undo_hint),
            )
        }
    }
}
@Composable
private fun ReviewStage(
    uiState: VoiceInputUiState,
    onInsert: () -> Unit,
    onDiscard: () -> Unit,
    onRefine: () -> Unit,
) {
    val prefs by VoxKBPreferenceStore
    val refinementEnabled by prefs.voice.refinementEnabled.collectAsState()
    val reviewText = uiState.reviewText
    val wordCount = remember(reviewText) {
        reviewText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }

    SnyggColumn(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SnyggBox(
            elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
                    attributes = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("text")),
                    text = reviewText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__word_count, wordCount),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SnyggRow(
            elementName = VoxKBImeUi.VoiceActionBar.elementName,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val primaryAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("primary"))
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                attributes = primaryAttr,
                onClick = onInsert,
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    attributes = primaryAttr,
                    text = stringResource(R.string.voice__insert),
                )
            }

            if (refinementEnabled && !uiState.isRefined) {
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    onClick = onRefine,
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        text = stringResource(R.string.voice__refine),
                    )
                }
            }

            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                onClick = onDiscard,
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__dismiss),
                )
            }
        }
    }
}

@Composable
private fun RecordingStage(
    state: VoiceInputState,
    amplitudeHistory: List<Float>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val isRecording = state == VoiceInputState.RECORDING
    val prefs by VoxKBPreferenceStore
    val animationStyle by prefs.voice.animationStyle.collectAsState()
    val refinementEnabled by prefs.voice.refinementEnabled.collectAsState()
    val refinementStyle by prefs.voice.refinementStyle.collectAsState()
    val isAgentMode = refinementEnabled && refinementStyle == RefinementStyle.AGENT

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SnyggColumn(
            elementName = VoxKBImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The mic and visualization share one center anchor. Keeping them in
            // the same layer prevents the labels below from shifting the mic away
            // from the waveform's horizontal centerline.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (animationStyle == VoiceAnimationStyle.WAVEFORM_BARS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    ) {
                        VoiceWaveform(
                            history = amplitudeHistory,
                            isActive = isRecording,
                        )
                    }
                }
                VoiceMicButton(
                    isRecording = isRecording,
                    animationStyle = animationStyle,
                    amplitude = amplitudeHistory.lastOrNull() ?: 0f,
                    onToggle = { if (isRecording) onStopRecording() else onStartRecording() },
                    onRelease = onStopRecording,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceProcessing.elementName,
                text = stringResource(
                    when {
                        isAgentMode && !isRecording -> R.string.voice__agent_tap_hint
                        isAgentMode -> R.string.voice__agent_listening
                        isRecording -> R.string.voice__listening
                        else -> R.string.voice__tap_to_start
                    }
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceInputRoot.elementName,
                text = stringResource(
                    if (isRecording) R.string.voice__listening_sublabel
                    else R.string.voice__tap_to_start_sublabel
                ),
            )
        }
    }
}
@Composable
private fun PulseRings(amplitude: Float) {
    val micStyle = rememberSnyggThemeQuery(
        VoxKBImeUi.VoiceMicButton.elementName,
        attributes = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("recording")),
    )
    val ringColor = micStyle.background()
    val transition = rememberInfiniteTransition(label = "pulse")

    val progress0 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_0",
    )
    val progress1 by transition.animateFloat(
        initialValue = -1f / 3f,
        targetValue = 2f / 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_1",
    )
    val progress2 by transition.animateFloat(
        initialValue = -2f / 3f,
        targetValue = 1f / 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_2",
    )

    val minRadiusDp = 38.dp
    val maxRadiusDp = 78.dp
    val density = LocalDensity.current

    Canvas(modifier = Modifier.size(160.dp)) {
        val minR = with(density) { minRadiusDp.toPx() }
        val maxR = with(density) { maxRadiusDp.toPx() }
        val strokeW = with(density) { 2.dp.toPx() }

        for (progress in listOf(progress0, progress1, progress2)) {
            val normalized = progress.coerceIn(0f, 1f)
            if (normalized <= 0f) continue
            val radius = minR + normalized * (maxR - minR) + amplitude * 10.dp.toPx()
            val alpha = (1f - normalized).let { it * it } * (0.35f + amplitude * 0.45f)
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = strokeW),
            )
        }
    }
}

@Composable
private fun WaveCircleAnimation(color: Color, amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-progress",
    )
    val density = LocalDensity.current
    Canvas(modifier = Modifier.size(160.dp)) {
        val minR = with(density) { 38.dp.toPx() }
        val maxR = with(density) { 78.dp.toPx() }
        val radius = minR + progress * (maxR - minR) + amplitude * 14.dp.toPx()
        val alpha = (0.35f + amplitude * 0.5f) * (1f - progress)
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun GlowingOrbAnimation(color: Color, amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    val scale = 0.86f + pulse * 0.14f + amplitude * 0.28f
    val alpha = 0.22f + pulse * 0.2f + amplitude * 0.45f
    Canvas(modifier = Modifier.size(160.dp)) {
        val radius = size.minDimension / 2f * scale
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun ParticleBurstAnimation(color: Color, amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle-progress",
    )
    val density = LocalDensity.current
    val directions = remember { List(8) { index ->
        val angle = index * 45f * (PI / 180f).toFloat()
        Offset(cos(angle), sin(angle))
    } }
    Canvas(modifier = Modifier.size(160.dp)) {
        val maxRadius = with(density) { (54 + amplitude * 22).dp.toPx() }
        val particleRadius = with(density) { (2.5f + amplitude * 2.5f).dp.toPx() }
        for (dir in directions) {
            val distance = progress * maxRadius
            val alpha = 1f - progress
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = particleRadius,
                center = center + dir * distance,
            )
        }
    }
}

@Composable
private fun VoiceMicButton(
    isRecording: Boolean,
    animationStyle: VoiceAnimationStyle = VoiceAnimationStyle.WAVEFORM_BARS,
    amplitude: Float,
    onToggle: () -> Unit,
    onRelease: () -> Unit,
) {
    val stateAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf(if (isRecording) "recording" else "idle"))
    val micStyle = rememberSnyggThemeQuery(
        VoxKBImeUi.VoiceMicButton.elementName,
        attributes = stateAttr,
    )
    val currentIsRecording by rememberUpdatedState(isRecording)
    var pressed by remember { mutableStateOf(false) }
    // True only between the press start (from idle) and the release, so a quick
    // tap doesn't fire start twice (once on press, once on tap).
    var pressStartedRecording by remember { mutableStateOf(false) }

    val targetScale = if (pressed) 0.92f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mic-press",
    )

    val bgColor = micStyle.background()
    // Border tracks the button's own theme color so it adapts to light/dark and
    // custom themes instead of the previous hardcoded red/blue literals.
    val borderColor = bgColor.copy(alpha = 0.55f)
    val darkerColor = Color(bgColor.red * 0.8f, bgColor.green * 0.8f, bgColor.blue * 0.8f)

    val micDesc = stringResource(
        if (isRecording) R.string.voice__stop_recording else R.string.voice__start_recording
    )

    Box(
        modifier = Modifier.size(168.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            // Soft outer glow behind the mic.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(bgColor.copy(alpha = 0.25f), Color.Transparent),
                                center = center,
                                radius = size.maxDimension / 2f,
                            ),
                            radius = size.maxDimension / 2f,
                            center = center,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {}

            val reactiveAmplitude by animateFloatAsState(
                targetValue = amplitude.coerceIn(0f, 1f),
                animationSpec = tween(90),
                label = "voice-energy",
            )
            when (animationStyle) {
                VoiceAnimationStyle.WAVEFORM_BARS -> Unit
                VoiceAnimationStyle.RIPPLE_RINGS -> PulseRings(reactiveAmplitude)
                VoiceAnimationStyle.WAVE_CIRCLE -> WaveCircleAnimation(bgColor, reactiveAmplitude)
                VoiceAnimationStyle.GLOWING_ORB -> GlowingOrbAnimation(bgColor, reactiveAmplitude)
                VoiceAnimationStyle.PARTICLE_BURST -> ParticleBurstAnimation(bgColor, reactiveAmplitude)
            }
        }

        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale)
                .drawBehind {
                    drawCircle(color = bgColor)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(bgColor, darkerColor),
                            center = center,
                            radius = size.minDimension / 2f,
                        ),
                        radius = size.minDimension / 2f,
                        center = center,
                    )
                    drawCircle(
                        color = borderColor,
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                .clip(CircleShape)
                .semantics { contentDescription = micDesc }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val pressStart = System.currentTimeMillis()
                        val startedIdle = !currentIsRecording
                        pressStartedRecording = startedIdle
                        if (startedIdle) {
                            // Press-to-talk from idle starts recording immediately.
                            onToggle()
                        }
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up == null) {
                            // Gesture cancelled (e.g. finger slid off). If we started
                            // recording on press (press-to-talk), stop it so it doesn't
                            // run forever; a tap-to-toggle recording is left as-is.
                            if (pressStartedRecording) onRelease()
                            pressStartedRecording = false
                            return@awaitEachGesture
                        }
                        val pressDuration = System.currentTimeMillis() - pressStart
                        if (startedIdle && pressDuration < 200L) {
                            // Quick tap from idle: recording already started on press,
                            // so we DO NOT toggle again — this was the double-start bug.
                        } else {
                            // Long-press release, or a tap while already recording: stop.
                            onRelease()
                        }
                        pressStartedRecording = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceMicButtonIcon.elementName,
                attributes = stateAttr,
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    history: List<Float>,
    isActive: Boolean,
) {
    val rootStyle = rememberSnyggThemeQuery(VoxKBImeUi.VoiceInputRoot.elementName)
    val waveformStyle = rememberSnyggThemeQuery(VoxKBImeUi.VoiceWaveform.elementName)
    val waveformClipStyle = rememberSnyggThemeQuery(
        VoxKBImeUi.VoiceWaveform.elementName,
        attributes = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("clipping")),
    )
    val foreground = rootStyle.foreground()
    val activeColor = waveformStyle.foreground()
    val clippingColor = waveformClipStyle.foreground()

    val barCount = WAVEFORM_BAR_COUNT
    val barWidthDp = 4.dp
    val barGapDp = 3.dp
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidthDp.toPx() }
    val barGapPx = with(density) { barGapDp.toPx() }

    val smoothedValues = remember { FloatArray(barCount) { 0f } }
    val displayValues = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }

    if (isActive && history.isNotEmpty()) {
        val n = history.size
        for (i in 0 until barCount) {
            val t = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val idxF = t * (n - 1)
            val i0 = idxF.toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceAtMost(n - 1)
            val frac = idxF - i0
            val raw = (history[i0] * (1f - frac) + history[i1] * frac).coerceIn(0f, 1f)
            smoothedValues[i] = smoothedValues[i] * 0.55f + raw * 0.45f
            displayValues[i] = smoothedValues[i]
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            // Smooth ease-out decay toward zero instead of the previous per-frame
            // multiplicative stepping, which produced visible stair-stepping.
            while (true) {
                var allZero = true
                for (i in 0 until barCount) {
                    val v = displayValues[i]
                    val next = v - (v * 0.18f + 0.01f)
                    displayValues[i] = if (next < 0.004f) 0f else next
                    if (displayValues[i] > 0f) allZero = false
                }
                if (allZero) break
                delay(16)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val totalWidth = barCount * (barWidthPx + barGapPx) - barGapPx
        val startX = (size.width - totalWidth) / 2f
        // True vertical center (was 0.55f, which sat below the mic).
        val centerY = size.height * 0.5f
        // Capped so bars flank the 64dp mic rather than drawing over it.
        val maxBarH = size.height * 0.18f

        for (i in 0 until barCount) {
            val v = displayValues[i]
            val shaped = v.pow(0.65f)
            val barH = (shaped * maxBarH).coerceAtLeast(barWidthPx)
            val x = startX + i * (barWidthPx + barGapPx)

            val color = when {
                shaped > 0.68f && isActive -> clippingColor.copy(alpha = 0.3f + shaped * 0.7f)
                isActive -> activeColor.copy(alpha = 0.3f + shaped * 0.7f)
                else -> foreground.copy(alpha = 0.12f)
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(barWidthPx / 2f),
            )
        }
    }
}

private fun Float.pow(p: Float): Float = Math.pow(this.toDouble(), p.toDouble()).toFloat()

@Composable
private fun ProcessingStage(
    label: String,
    sublabel: String,
    onCancel: () -> Unit,
) {
    val processingStyle = rememberSnyggThemeQuery(VoxKBImeUi.VoiceProcessing.elementName)
    val indicatorColor = processingStyle.foreground()

    val transition = rememberInfiniteTransition(label = "processing")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processing-pulse",
    )
    val scale = 0.8f + pulse * 0.4f
    val indicatorAlpha = 0.5f + pulse * 0.5f

    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "label-shimmer",
    )
    val labelAlpha = 0.5f + shimmer * 0.5f

    SnyggColumn(
        elementName = VoxKBImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = indicatorAlpha
                }
                .drawBehind {
                    drawCircle(
                        color = indicatorColor,
                        radius = size.minDimension / 2f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {}

        Spacer(modifier = Modifier.height(24.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = label,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
        Spacer(modifier = Modifier.height(6.dp))
        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = sublabel,
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggButton(
            elementName = VoxKBImeUi.VoiceActionKey.elementName,
            onClick = onCancel,
        ) {
            SnyggText(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                text = stringResource(R.string.voice__cancel),
            )
        }
    }
}

@Composable
private fun ErrorStage(
    message: String,
    rawTranscript: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onInsertRaw: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val showSettingsCta = message.contains("not configured", ignoreCase = true) ||
        message.contains("Settings", ignoreCase = true)
    val errorAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("error"))

    SnyggColumn(
        elementName = VoxKBImeUi.VoiceProcessing.elementName,
        attributes = errorAttr,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggBox(
            elementName = VoxKBImeUi.VoiceMicButton.elementName,
            attributes = errorAttr,
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceMicButtonIcon.elementName,
                attributes = errorAttr,
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            attributes = errorAttr,
            text = stringResource(R.string.voice__error_title),
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = message,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        if (rawTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            SnyggText(
                elementName = VoxKBImeUi.VoiceProcessing.elementName,
                text = stringResource(R.string.voice__raw_transcript_hint),
                modifier = Modifier.widthIn(max = 320.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            SnyggBox(
                elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .heightIn(max = 84.dp),
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
                        attributes = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("text")),
                        text = rawTranscript,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        SnyggRow(
            elementName = VoxKBImeUi.VoiceActionBar.elementName,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                onClick = onDismiss,
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__dismiss),
                )
            }
            if (showSettingsCta) {
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    onClick = onOpenSettings,
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        text = stringResource(R.string.voice__open_settings),
                    )
                }
            }
            if (rawTranscript.isNotBlank()) {
                val primaryAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("primary"))
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    attributes = primaryAttr,
                    onClick = onInsertRaw,
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        attributes = primaryAttr,
                        text = stringResource(R.string.voice__use_raw_transcript),
                    )
                }
            }
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                onClick = onRetry,
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__try_again),
                )
            }
        }
    }
}

@Composable
private fun PermissionStage(
    onRequestPermission: () -> Unit,
) {
    SnyggColumn(
        elementName = VoxKBImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggBox(
            elementName = VoxKBImeUi.VoiceMicButton.elementName,
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceMicButtonIcon.elementName,
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__permission_title),
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__permission_description),
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggButton(
            elementName = VoxKBImeUi.VoiceActionKey.elementName,
            onClick = onRequestPermission,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                text = stringResource(R.string.voice__grant_permission),
            )
        }
    }
}

@Composable
private fun SetupStage(
    message: String,
    canUseTranscribeOnly: Boolean,
    onOpenSettings: () -> Unit,
    onUseTranscribeOnly: () -> Unit,
) {
    SnyggColumn(
        elementName = VoxKBImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggBox(
            elementName = VoxKBImeUi.VoiceMicButton.elementName,
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceMicButtonIcon.elementName,
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__setup_title),
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = message,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggRow(
            elementName = VoxKBImeUi.VoiceActionBar.elementName,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val primaryAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("primary"))
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                attributes = primaryAttr,
                onClick = onOpenSettings,
            ) {
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    attributes = primaryAttr,
                    text = stringResource(R.string.voice__open_settings),
                )
            }
            if (canUseTranscribeOnly) {
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    onClick = onUseTranscribeOnly,
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        text = stringResource(R.string.voice__use_transcribe_only),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceBottomBar(
    keyboardManager: com.voxkb.ime.keyboard.KeyboardManager,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
) {
    val enterDesc = stringResource(R.string.voice__enter)
    val switchDesc = stringResource(R.string.voice__switch_keyboard)
    val backspaceDesc = stringResource(R.string.voice__backspace)
    val undoDesc = stringResource(R.string.voice__undo)
    val redoDesc = stringResource(R.string.voice__redo_desc)
    val spaceLabel = stringResource(R.string.voice__space)

    SnyggRow(
        elementName = VoxKBImeUi.VoiceBottomBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = onBackspace,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = backspaceDesc,
                modifier = Modifier.size(20.dp),
            )
        }

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            enabled = canUndo,
            onClick = onUndo,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = undoDesc,
                modifier = Modifier.size(20.dp),
            )
        }

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            enabled = canRedo,
            onClick = onRedo,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = redoDesc,
                modifier = Modifier.size(20.dp),
            )
        }

        SnyggButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            onClick = onSpace,
            modifier = Modifier.weight(1.25f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggText(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                text = spaceLabel,
            )
        }

        val abcAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf("primary"))
        SnyggButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            attributes = abcAttr,
            onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
            modifier = Modifier.weight(1.6f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                attributes = abcAttr,
                imageVector = Icons.Filled.Keyboard,
                contentDescription = switchDesc,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                attributes = abcAttr,
                text = stringResource(R.string.voice__abc),
            )
        }

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = { VoxKBImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER) },
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = enterDesc,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun rememberOpenVoiceSettings(): () -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("ui://voxkb/settings/voice"),
            ).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(ctx.packageName)
            }
            runCatching { ctx.startActivity(intent) }
        }
    }
}
