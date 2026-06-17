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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
import com.voxkb.lib.snygg.ui.SnyggColumn
import com.voxkb.lib.snygg.ui.SnyggIcon
import com.voxkb.lib.snygg.ui.SnyggIconButton
import com.voxkb.lib.snygg.ui.SnyggRow
import com.voxkb.lib.snygg.ui.SnyggText
import com.voxkb.lib.snygg.ui.rememberSnyggThemeQuery

private const val STATE_TRANSITION_MS = 280
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
    val keyboardManager by ctx.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()
    val prefs by VoxKBPreferenceStore
    val toneStyle by prefs.voice.toneStyle.collectAsState()

    val openSettings = rememberOpenVoiceSettings()

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    SnyggBox(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(VoxKBImeSizing.imeUiHeight())
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffsetX < -140f) {
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-260f, 0f)
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
                toneStyle = toneStyle,
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
                onUndo = { voiceInputManager.clearLastTranscription() },
                onRedo = { voiceInputManager.startRecording() },
            )
        }
    }
}

@Composable
private fun VoiceTopBar(
    toneStyle: ToneStyle,
    isRecording: Boolean,
    durationMs: Long,
    onClose: () -> Unit,
) {
    val closeDesc = stringResource(R.string.voice__close)
    var showDropdown by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val prefs by VoxKBPreferenceStore
    val refinementEnabled by prefs.voice.refinementEnabled.collectAsState()

    val timerAlpha by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = tween(300),
        label = "timer-alpha",
    )

    SnyggRow(
        elementName = VoxKBImeUi.VoiceTopBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SnyggChip(
                elementName = VoxKBImeUi.VoiceChip.elementName,
                onClick = { showDropdown = true },
                imageVector = Icons.Outlined.AutoAwesome,
                text = toneStyle.displayName(),
            )

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                ToneStyle.entries.forEach { tone ->
                    DropdownMenuItem(
                        text = {
                            SnyggText(
                                elementName = VoxKBImeUi.VoiceChipText.elementName,
                                text = tone.displayName(),
                            )
                        },
                        leadingIcon = {
                            SnyggIcon(
                                elementName = VoxKBImeUi.VoiceChip.elementName,
                                imageVector = tone.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (tone == toneStyle) {
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
                                prefs.voice.toneStyle.set(tone)
                            }
                            showDropdown = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        val toggleStateAttr = if (refinementEnabled)
            mapOf(VoxKBImeUi.Attr.VoiceState to listOf("selected")) else emptyMap()
        SnyggChip(
            elementName = VoxKBImeUi.VoiceEnhanceToggle.elementName,
            attributes = toggleStateAttr,
            onClick = {
                coroutineScope.launch {
                    val current = prefs.voice.refinementEnabled.get()
                    prefs.voice.refinementEnabled.set(!current)
                }
            },
            imageVector = Icons.Outlined.AutoAwesome,
            text = if (refinementEnabled) "Refine" else "Raw",
        )

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
                scaleIn(initialScale = 0.96f, animationSpec = tween(STATE_TRANSITION_MS)))
                .togetherWith(
                    fadeOut(tween(STATE_TRANSITION_MS)) +
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
            VoiceInputState.INSERTING -> AutoInsertStage(uiState = uiState)
            VoiceInputState.SUCCESS -> SuccessStage(
                uiState = uiState,
                refinementEnabled = manager.isRefinementEnabled(),
                onTextChange = { manager.updateTranscript(it) },
                onInsert = { manager.commitText() },
                onRedo = { manager.startRecording() },
                onRefine = { manager.refineText() },
                onToggleRawRefined = { manager.toggleRefined() },
                onDismiss = { manager.reset() },
                isAgentMode = uiState.isAgentMode,
            )
            VoiceInputState.ERROR -> ErrorStage(
                message = uiState.errorMessage.ifBlank { stringResource(R.string.voice__error_title) },
                onRetry = { manager.startRecording() },
                onDismiss = { manager.reset() },
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
        }
    }
}

@Composable
private fun AutoInsertStage(uiState: VoiceInputUiState) {
    SnyggBox(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(
            elementName = VoxKBImeUi.VoiceInputRoot.elementName,
            text = if (uiState.done) stringResource(R.string.voice__done)
                   else stringResource(R.string.voice__inserting),
        )
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        VoiceWaveform(
            history = amplitudeHistory,
            isActive = isRecording,
        )

        SnyggColumn(
            elementName = VoxKBImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VoiceMicButton(
                isRecording = isRecording,
                onPress = onStartRecording,
                onRelease = onStopRecording,
                onTap = {
                    if (isRecording) onStopRecording() else onStartRecording()
                },
            )
            Spacer(modifier = Modifier.height(20.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceProcessing.elementName,
                text = stringResource(
                    if (isRecording) R.string.voice__listening else R.string.voice__tap_to_start
                ),
            )
        }
    }
}

@Composable
private fun PulseRings() {
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

    val minRadiusDp = 32.dp
    val maxRadiusDp = 60.dp
    val density = LocalDensity.current

    Canvas(modifier = Modifier.size(120.dp)) {
        val minR = with(density) { minRadiusDp.toPx() }
        val maxR = with(density) { maxRadiusDp.toPx() }
        val strokeW = with(density) { 2.dp.toPx() }

        for (progress in listOf(progress0, progress1, progress2)) {
            val normalized = progress.coerceIn(0f, 1f)
            if (normalized <= 0f) continue
            val radius = minR + normalized * (maxR - minR)
            val alpha = (1f - normalized) * 0.4f
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
private fun VoiceMicButton(
    isRecording: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onTap: () -> Unit,
) {
    val stateAttr = mapOf(VoxKBImeUi.Attr.VoiceState to listOf(if (isRecording) "recording" else "idle"))
    val micStyle = rememberSnyggThemeQuery(
        VoxKBImeUi.VoiceMicButton.elementName,
        attributes = stateAttr,
    )
    val currentIsRecording by rememberUpdatedState(isRecording)
    var pressed by remember { mutableStateOf(false) }

    val targetScale = if (pressed) 0.94f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mic-press",
    )

    val bgColor = micStyle.background()
    // Border tracks the button's own theme color so it adapts to light/dark and
    // custom themes instead of the previous hardcoded red/blue literals.
    val borderColor = bgColor.copy(alpha = 0.65f)

    val micDesc = stringResource(
        if (isRecording) R.string.voice__stop_recording else R.string.voice__start_recording
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            PulseRings()
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .drawBehind {
                    drawCircle(color = bgColor)
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
                        if (!currentIsRecording) {
                            onPress()
                        }
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up == null) return@awaitEachGesture
                        val pressDuration = System.currentTimeMillis() - pressStart
                        if (pressDuration < 200L) {
                            onTap()
                        } else {
                            onRelease()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceMicButtonIcon.elementName,
                attributes = stateAttr,
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
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

    SnyggColumn(
        elementName = VoxKBImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            color = indicatorColor,
            strokeWidth = 5.dp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        SnyggText(
            elementName = VoxKBImeUi.VoiceProcessing.elementName,
            text = label,
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
private fun SuccessStage(
    uiState: VoiceInputUiState,
    refinementEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onInsert: () -> Unit,
    onRedo: () -> Unit,
    onRefine: () -> Unit,
    onToggleRawRefined: () -> Unit,
    onDismiss: () -> Unit,
    isAgentMode: Boolean,
) {
    val text = uiState.transcribedText
    val hasRawAndRefined = uiState.rawTranscribedText.isNotBlank() && uiState.refinedText.isNotBlank()
    val transcriptStyle = rememberSnyggThemeQuery(VoxKBImeUi.VoiceTranscriptBox.elementName)

    SnyggColumn(
        elementName = VoxKBImeUi.VoiceInputRoot.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        SnyggRow(
            elementName = VoxKBImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggText(
                elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
                text = when {
                    isAgentMode && uiState.isRefined -> stringResource(R.string.voice__generated_result)
                    isAgentMode -> stringResource(R.string.voice__instruction)
                    uiState.isRefined -> stringResource(R.string.voice__refined_transcript)
                    else -> stringResource(R.string.voice__transcript)
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            SnyggText(
                elementName = VoxKBImeUi.VoiceInputRoot.elementName,
                text = stringResource(R.string.voice__word_count, countWords(text)),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SnyggBox(
            elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (text.isBlank()) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceTranscriptBox.elementName,
                        text = stringResource(R.string.voice__empty_result),
                    )
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = TextStyle(color = transcriptStyle.foreground()),
                        cursorBrush = SolidColor(transcriptStyle.foreground()),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SnyggRow(
            elementName = VoxKBImeUi.VoiceActionBar.elementName,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                onClick = onRedo,
                modifier = Modifier.weight(1f),
            ) {
                SnyggIcon(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__redo),
                )
            }
            if (refinementEnabled && !uiState.isRefined) {
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    onClick = onRefine,
                    modifier = Modifier.weight(1f),
                ) {
                    SnyggIcon(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        imageVector = if (isAgentMode) Icons.Filled.Send else Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        text = if (isAgentMode) stringResource(R.string.voice__generate)
                               else stringResource(R.string.voice__refine),
                    )
                }
            }
            if (hasRawAndRefined) {
                SnyggButton(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    onClick = onToggleRawRefined,
                    modifier = Modifier.weight(1f),
                ) {
                    SnyggText(
                        elementName = VoxKBImeUi.VoiceActionKey.elementName,
                        text = if (isAgentMode) {
                            if (uiState.isRefined) stringResource(R.string.voice__instruction)
                            else stringResource(R.string.voice__generated_result)
                        } else {
                            if (uiState.isRefined) stringResource(R.string.voice__raw)
                            else stringResource(R.string.voice__refined)
                        },
                    )
                }
            }
            SnyggButton(
                elementName = VoxKBImeUi.VoiceActionKey.elementName,
                onClick = onInsert,
                modifier = Modifier.weight(1.2f),
            ) {
                SnyggIcon(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SnyggText(
                    elementName = VoxKBImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__insert),
                )
            }
        }
    }
}

@Composable
private fun ErrorStage(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
private fun VoiceBottomBar(
    keyboardManager: com.voxkb.ime.keyboard.KeyboardManager,
    onBackspace: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val enterDesc = stringResource(R.string.voice__enter)
    val switchDesc = stringResource(R.string.voice__switch_keyboard)
    val backspaceDesc = stringResource(R.string.voice__backspace)
    val undoDesc = stringResource(R.string.voice__undo)
    val redoDesc = stringResource(R.string.voice__redo_desc)
    val rootStyle = rememberSnyggThemeQuery(VoxKBImeUi.VoiceInputRoot.elementName)
    val dividerColor = rootStyle.foreground().copy(alpha = 0.12f)

    SnyggRow(
        elementName = VoxKBImeUi.VoiceBottomBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = onBackspace,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = backspaceDesc,
                modifier = Modifier.size(16.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .width(0.5.dp)
                .background(dividerColor)
        )

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = onUndo,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.Undo,
                contentDescription = undoDesc,
                modifier = Modifier.size(16.dp),
            )
        }

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = onRedo,
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.Refresh,
                contentDescription = redoDesc,
                modifier = Modifier.size(16.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .width(0.5.dp)
                .background(dividerColor)
        )

        SnyggButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
            modifier = Modifier.weight(1.6f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.Keyboard,
                contentDescription = switchDesc,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SnyggText(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                text = stringResource(R.string.voice__abc),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .width(0.5.dp)
                .background(dividerColor)
        )

        SnyggIconButton(
            elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = { VoxKBImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER) },
        ) {
            SnyggIcon(
                elementName = VoxKBImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = enterDesc,
                modifier = Modifier.size(16.dp),
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(ctx.packageName)
            }
            runCatching { ctx.startActivity(intent) }
        }
    }
}

private fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    return text.trim().split(Regex("\\s+")).size
}
