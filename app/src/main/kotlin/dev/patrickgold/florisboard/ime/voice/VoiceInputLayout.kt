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
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private const val STATE_TRANSITION_MS = 280
private const val RING_EXPAND_MS = 1800
private const val WAVEFORM_BAR_COUNT = 28

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
    val prefs by FlorisPreferenceStore
    val refinementStyle by prefs.voice.refinementStyle.collectAsState()
    val animationStyle by prefs.voice.animationStyle.collectAsState()

    val openSettings = rememberOpenVoiceSettings()

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    SnyggBox(
        elementName = FlorisImeUi.VoiceInputRoot.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
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
            elementName = FlorisImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            VoiceTopBar(
                refinementStyle = refinementStyle,
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
                    refinementStyle = refinementStyle,
                    animationStyle = animationStyle,
                )
            }

            VoiceBottomBar(
                keyboardManager = keyboardManager,
                state = uiState.state,
                onCancelProcessing = { voiceInputManager.cancel() },
            )
        }
    }
}

@Composable
private fun VoiceTopBar(
    refinementStyle: RefinementStyle,
    onClose: () -> Unit,
) {
    val closeDesc = stringResource(R.string.voice__close)
    var showDropdown by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore

    SnyggRow(
        elementName = FlorisImeUi.VoiceTopBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Mode selector dropdown
        Box(modifier = Modifier.padding(end = 8.dp)) {
            SnyggButton(
                elementName = FlorisImeUi.VoiceTopBar.elementName,
                onClick = { showDropdown = true },
            ) {
                SnyggText(
                    elementName = FlorisImeUi.VoiceTopBar.elementName,
                    text = refinementStyle.displayName(),
                )
                Spacer(modifier = Modifier.width(4.dp))
                SnyggIcon(
                    elementName = FlorisImeUi.VoiceTopBar.elementName,
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Dropdown menu
            androidx.compose.material3.DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                RefinementStyle.entries
                    .filter { it != RefinementStyle.CUSTOM }
                    .forEach { style ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                androidx.compose.material3.Text(style.displayName())
                            },
                            onClick = {
                                coroutineScope.launch {
                                    prefs.voice.refinementStyle.set(style)
                                }
                                showDropdown = false
                            },
                        )
                    }
            }
        }

        SnyggIconButton(
            elementName = FlorisImeUi.VoiceTopBar.elementName,
            modifier = Modifier.size(32.dp),
            onClick = onClose,
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceTopBar.elementName,
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
    keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager,
    openSettings: () -> Unit,
    ctx: Context,
    refinementStyle: RefinementStyle,
    animationStyle: VoiceAnimationStyle,
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
                amplitude = uiState.amplitude,
                amplitudeHistory = uiState.amplitudeHistory,
                durationMs = uiState.durationMs,
                refinementEnabled = manager.isRefinementEnabled(),
                isAgentMode = refinementStyle.isAgent,
                animationStyle = animationStyle,
                onStartRecording = { manager.startRecording() },
                onStopRecording = { manager.stopRecording() },
                onToggleRefinement = { manager.toggleRefinement() },
                onUndo = { manager.clearLastTranscription() },
                onBackspace = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE) },
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
                    val intent = Intent(ctx, FlorisAppActivity::class.java).apply {
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
private fun RecordingStage(
    state: VoiceInputState,
    amplitude: Float,
    amplitudeHistory: List<Float>,
    durationMs: Long,
    refinementEnabled: Boolean,
    isAgentMode: Boolean,
    animationStyle: VoiceAnimationStyle,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleRefinement: () -> Unit,
    onUndo: () -> Unit,
    onBackspace: () -> Unit,
) {
    val timerDesc = stringResource(R.string.voice__listening)

    SnyggColumn(
        elementName = FlorisImeUi.VoiceInputRoot.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mic button with Undo and Backspace buttons
        SnyggRow(
            elementName = FlorisImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Undo button
            SnyggIconButton(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                modifier = Modifier.size(48.dp),
                onClick = onUndo,
            ) {
                SnyggIcon(
                    elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                    imageVector = Icons.Filled.Undo,
                    contentDescription = stringResource(R.string.voice__undo),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Backspace button
            SnyggIconButton(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                modifier = Modifier.size(48.dp),
                onClick = onBackspace,
            ) {
                SnyggIcon(
                    elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                    imageVector = Icons.Filled.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.voice__backspace),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state == VoiceInputState.RECORDING) {
                    when (animationStyle) {
                        VoiceAnimationStyle.RIPPLE_RINGS -> RippleRings(amplitude = amplitude)
                        VoiceAnimationStyle.WAVE_CIRCLE -> WaveCircle(amplitude = amplitude)
                        VoiceAnimationStyle.GLOWING_ORB -> GlowingOrb(amplitude = amplitude)
                        VoiceAnimationStyle.PARTICLE_BURST -> ParticleBurst(amplitude = amplitude)
                    }
                }
                VoiceMicButton(
                    isRecording = state == VoiceInputState.RECORDING,
                    onPress = onStartRecording,
                    onRelease = onStopRecording,
                    onTap = {
                        if (state == VoiceInputState.RECORDING) onStopRecording() else onStartRecording()
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        VoiceWaveform(
            history = amplitudeHistory,
            isActive = state == VoiceInputState.RECORDING,
        )

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceInputRoot.elementName,
            text = formatDuration(durationMs),
            modifier = Modifier.semantics {
                contentDescription = timerDesc
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceInputRoot.elementName,
            text = when (state) {
                VoiceInputState.RECORDING -> stringResource(R.string.voice__listening)
                else -> stringResource(R.string.voice__tap_to_start)
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        EnhanceToggle(
            enabled = refinementEnabled,
            isAgentMode = isAgentMode,
            onToggle = onToggleRefinement,
        )
    }
}

@Composable
private fun RippleRings(amplitude: Float) {
    val micStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
    )
    val ringColor = micStyle.background()
    val transition = rememberInfiniteTransition(label = "pulse")
    for (i in 0 until 4) {
        val phase = i / 4f
        val progress by transition.animateFloat(
            initialValue = phase,
            targetValue = phase + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = RING_EXPAND_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring_$i",
        )
        val normalized = ((progress - phase) * 4f).coerceIn(0f, 1f)
        val color = ringColor.copy(alpha = (1f - normalized) * 0.5f)
        val baseRadius = 70f
        val ampBoost = amplitude * 18f
        val radiusDp = (baseRadius + normalized * 90f + ampBoost).dp
        val strokeDp = (3f - normalized * 2f).coerceAtLeast(1f).dp
        Canvas(modifier = Modifier.size(260.dp)) {
            drawCircle(
                color = color,
                radius = radiusDp.toPx(),
                center = center,
                style = Stroke(width = strokeDp.toPx()),
            )
        }
    }
}

@Composable
private fun WaveCircle(amplitude: Float) {
    val micStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
    )
    val barColor = micStyle.background().copy(alpha = 0.7f)
    val barCount = 24
    val radius = 70f
    val barWidth = 4f
    val maxBarHeight = 30f

    Canvas(modifier = Modifier.size(220.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val angleStep = 360f / barCount

        for (i in 0 until barCount) {
            val angle = (i * angleStep) * (Math.PI / 180f).toFloat()
            val ampBoost = amplitude * maxBarHeight
            val barHeight = (6f + ampBoost * 0.8f).coerceIn(4f, maxBarHeight + 10f)

            val midRadius = radius - 8f + barHeight / 2f
            val midX = centerX + kotlin.math.cos(angle) * midRadius
            val midY = centerY + kotlin.math.sin(angle) * midRadius

            drawRoundRect(
                color = barColor,
                topLeft = Offset(midX - barWidth / 2f, midY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun GlowingOrb(amplitude: Float) {
    val micStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
    )
    val baseColor = micStyle.background()
    val transition = rememberInfiniteTransition(label = "glow-pulse")

    val glowScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-scale",
    )

    val alphaScale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-alpha",
    )

    val ampBoost = amplitude * 0.3f
    val finalScale = glowScale * (1f + ampBoost)
    val finalAlpha = (alphaScale + ampBoost * 0.3f).coerceIn(0.2f, 0.9f)

    Canvas(modifier = Modifier.size(180.dp)) {
        val glowRadius = 60f * finalScale
        drawCircle(
            color = baseColor.copy(alpha = finalAlpha * 0.3f),
            radius = glowRadius * 1.4f,
            center = center,
            blendMode = androidx.compose.ui.graphics.BlendMode.Screen
        )
        drawCircle(
            color = baseColor.copy(alpha = finalAlpha * 0.5f),
            radius = glowRadius * 1.2f,
            center = center,
        )
        drawCircle(
            color = baseColor.copy(alpha = finalAlpha * 0.7f),
            radius = glowRadius,
            center = center,
        )
    }
}

@Composable
private fun ParticleBurst(amplitude: Float) {
    val micStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
    )
    val particleColor = micStyle.background().copy(alpha = 0.6f)
    val transition = rememberInfiniteTransition(label = "particles")
    val particleCount = 12

    // Create all animations before entering Canvas
    val phases = List(particleCount) { i ->
        val phase = i / particleCount.toFloat()
        val progress by transition.animateFloat(
            initialValue = phase,
            targetValue = phase + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "particle_$i",
        )
        phase to progress
    }

    Canvas(modifier = Modifier.size(200.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = 50f
        val maxDistance = 70f + amplitude * 40f

        phases.forEach { (phase, progress) ->
            val normalized = ((progress - phase)).coerceIn(0f, 1f)
            val i = phases.indexOf(phase to progress)
            val angle = (i * 360f / particleCount + progress * 30f) * (Math.PI / 180f).toFloat()

            val distance = baseRadius + normalized * maxDistance
            val particleX = centerX + kotlin.math.cos(angle) * distance
            val particleY = centerY + kotlin.math.sin(angle) * distance

            val alpha = (1f - normalized) * 0.8f
            val size = (6f - normalized * 4f).coerceAtLeast(2f)

            drawCircle(
                color = particleColor.copy(alpha = alpha * amplitude.coerceIn(0.2f, 1f)),
                radius = size,
                center = Offset(particleX, particleY),
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
    val stateAttr = mapOf(FlorisImeUi.Attr.VoiceState to listOf(if (isRecording) "recording" else "idle"))
    val micStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButton.elementName,
        attributes = stateAttr,
    )
    val iconStyle = rememberSnyggThemeQuery(
        FlorisImeUi.VoiceMicButtonIcon.elementName,
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

    val breath = rememberInfiniteTransition(label = "breath")
    val breathScale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath-scale",
    )

    val showBreath = !isRecording
    val bgColor = micStyle.background()
    val iconColor = iconStyle.foreground()

    val micDesc = stringResource(
        if (isRecording) R.string.voice__stop_recording else R.string.voice__start_recording
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .scale(scale)
            .then(if (showBreath) Modifier.scale(breathScale) else Modifier)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(color = bgColor)
            }
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
            elementName = FlorisImeUi.VoiceMicButtonIcon.elementName,
            attributes = stateAttr,
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun VoiceWaveform(
    history: List<Float>,
    isActive: Boolean,
) {
    val waveformStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceWaveform.elementName)
    val activeColor = waveformStyle.foreground()
    val peakColor = activeColor.copy(alpha = 0.7f)
    val rootStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceInputRoot.elementName)
    val baseColor = rootStyle.foreground().copy(alpha = 0.3f)
    val barCount = WAVEFORM_BAR_COUNT
    val barWidth = 3.5.dp
    val barGap = 3.dp

    Canvas(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth(),
    ) {
        val totalBars = barCount
        val totalWidth = totalBars * (barWidth.toPx() + barGap.toPx()) - barGap.toPx()
        val startX = (size.width - totalWidth) / 2f
        val midY = size.height / 2f

        val n = history.size
        if (n == 0) return@Canvas
        for (i in 0 until totalBars) {
            val t = i.toFloat() / (totalBars - 1).coerceAtLeast(1)
            val idxF = t * (n - 1)
            val i0 = idxF.toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceAtMost(n - 1)
            val frac = idxF - i0
            val v = (history[i0] * (1f - frac) + history[i1] * frac)
                .coerceIn(0f, 1f)
            val shaped = v.pow(0.65f)
            val barH = (shaped * size.height * 0.95f).coerceAtLeast(barWidth.toPx())
            val x = startX + i * (barWidth.toPx() + barGap.toPx())
            val color = when {
                !isActive -> baseColor
                shaped > 0.7f -> peakColor
                else -> activeColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, midY - barH / 2f),
                size = Size(barWidth.toPx(), barH),
                cornerRadius = CornerRadius(barWidth.toPx() / 2f),
            )
        }
    }
}

private fun Float.pow(p: Float): Float = Math.pow(this.toDouble(), p.toDouble()).toFloat()

@Composable
private fun EnhanceToggle(
    enabled: Boolean,
    isAgentMode: Boolean,
    onToggle: () -> Unit,
) {
    val stateAttr = if (enabled) mapOf(FlorisImeUi.Attr.VoiceState to listOf("selected")) else emptyMap()
    SnyggChip(
        elementName = FlorisImeUi.VoiceEnhanceToggle.elementName,
        attributes = stateAttr,
        onClick = onToggle,
        enabled = true,
        imageVector = Icons.Outlined.AutoAwesome,
        text = if (isAgentMode) stringResource(R.string.voice__agent)
               else stringResource(R.string.voice__enhance),
    )
}

@Composable
private fun ProcessingStage(
    label: String,
    sublabel: String,
    onCancel: () -> Unit,
) {
    val processingStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceProcessing.elementName)
    val indicatorColor = processingStyle.foreground()

    SnyggColumn(
        elementName = FlorisImeUi.VoiceProcessing.elementName,
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
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = label,
        )
        Spacer(modifier = Modifier.height(6.dp))
        SnyggText(
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = sublabel,
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggButton(
            elementName = FlorisImeUi.VoiceActionKey.elementName,
            onClick = onCancel,
        ) {
            SnyggText(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
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
    val transcriptStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceTranscriptBox.elementName)

    SnyggColumn(
        elementName = FlorisImeUi.VoiceInputRoot.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        SnyggRow(
            elementName = FlorisImeUi.VoiceInputRoot.elementName,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggText(
                elementName = FlorisImeUi.VoiceTranscriptBox.elementName,
                text = when {
                    isAgentMode && uiState.isRefined -> stringResource(R.string.voice__generated_result)
                    isAgentMode -> stringResource(R.string.voice__instruction)
                    uiState.isRefined -> stringResource(R.string.voice__refined_transcript)
                    else -> stringResource(R.string.voice__transcript)
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            SnyggText(
                elementName = FlorisImeUi.VoiceInputRoot.elementName,
                text = stringResource(R.string.voice__word_count, countWords(text)),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SnyggBox(
            elementName = FlorisImeUi.VoiceTranscriptBox.elementName,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (text.isBlank()) {
                    SnyggText(
                        elementName = FlorisImeUi.VoiceTranscriptBox.elementName,
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
            elementName = FlorisImeUi.VoiceActionBar.elementName,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggButton(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                onClick = onRedo,
                modifier = Modifier.weight(1f),
            ) {
                SnyggIcon(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SnyggText(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__redo),
                )
            }
            if (refinementEnabled && !uiState.isRefined) {
                SnyggButton(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    onClick = onRefine,
                    modifier = Modifier.weight(1f),
                ) {
                    SnyggIcon(
                        elementName = FlorisImeUi.VoiceActionKey.elementName,
                        imageVector = if (isAgentMode) Icons.Filled.Send else Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SnyggText(
                        elementName = FlorisImeUi.VoiceActionKey.elementName,
                        text = if (isAgentMode) stringResource(R.string.voice__generate)
                               else stringResource(R.string.voice__refine),
                    )
                }
            }
            if (hasRawAndRefined) {
                SnyggButton(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    onClick = onToggleRawRefined,
                    modifier = Modifier.weight(1f),
                ) {
                    SnyggText(
                        elementName = FlorisImeUi.VoiceActionKey.elementName,
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
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                onClick = onInsert,
                modifier = Modifier.weight(1.2f),
            ) {
                SnyggIcon(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SnyggText(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
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

    SnyggColumn(
        elementName = FlorisImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggBox(
            elementName = FlorisImeUi.VoiceMicButton.elementName,
            attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceMicButtonIcon.elementName,
                attributes = mapOf(FlorisImeUi.Attr.VoiceState to listOf("recording")),
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__error_title),
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = message,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggRow(
            elementName = FlorisImeUi.VoiceActionBar.elementName,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnyggButton(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                onClick = onDismiss,
            ) {
                SnyggText(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    text = stringResource(R.string.voice__dismiss),
                )
            }
            if (showSettingsCta) {
                SnyggButton(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
                    onClick = onOpenSettings,
                ) {
                    SnyggText(
                        elementName = FlorisImeUi.VoiceActionKey.elementName,
                        text = stringResource(R.string.voice__open_settings),
                    )
                }
            }
            SnyggButton(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                onClick = onRetry,
            ) {
                SnyggText(
                    elementName = FlorisImeUi.VoiceActionKey.elementName,
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
        elementName = FlorisImeUi.VoiceProcessing.elementName,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnyggBox(
            elementName = FlorisImeUi.VoiceMicButton.elementName,
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceMicButtonIcon.elementName,
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__permission_title),
        )

        Spacer(modifier = Modifier.height(8.dp))

        SnyggText(
            elementName = FlorisImeUi.VoiceProcessing.elementName,
            text = stringResource(R.string.voice__permission_description),
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        SnyggButton(
            elementName = FlorisImeUi.VoiceActionKey.elementName,
            onClick = onRequestPermission,
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SnyggText(
                elementName = FlorisImeUi.VoiceActionKey.elementName,
                text = stringResource(R.string.voice__grant_permission),
            )
        }
    }
}

@Composable
private fun VoiceBottomBar(
    keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager,
    state: VoiceInputState,
    onCancelProcessing: () -> Unit,
) {
    val spaceDesc = stringResource(R.string.voice__space)
    val enterDesc = stringResource(R.string.voice__enter)
    val switchDesc = stringResource(R.string.voice__switch_keyboard)

    SnyggRow(
        elementName = FlorisImeUi.VoiceBottomBar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEFT side: Keyboard button
        SnyggButton(
            elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
            onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
            modifier = Modifier.weight(1.5f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.Keyboard,
                contentDescription = switchDesc,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SnyggText(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                text = stringResource(R.string.voice__abc),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // RIGHT side: Space button, Enter button
        SnyggButton(
            elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
            onClick = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.SPACE) },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggText(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                text = stringResource(R.string.voice__space),
            )
        }
        SnyggButton(
            elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
            onClick = { FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER) },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
        ) {
            SnyggIcon(
                elementName = FlorisImeUi.VoiceBottomBarButton.elementName,
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = enterDesc,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun VoiceChip(label: String) {
    SnyggChip(
        elementName = FlorisImeUi.VoiceChip.elementName,
        onClick = {},
        enabled = false,
        text = label,
    )
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
                Uri.parse("ui://florisboard/settings/voice"),
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
