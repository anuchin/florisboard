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

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager

// ════════════════════════════════════════════════════════════════════════════
//  Design tokens — semantic, dark-first, look great on any IME theme.
// ════════════════════════════════════════════════════════════════════════════

private object VoiceColors {
    val BgTop = Color(0xFF0B0D17)
    val BgMid = Color(0xFF11132A)
    val BgBottom = Color(0xFF181536)

    val MicIdleStart = Color(0xFF6366F1)
    val MicIdleEnd = Color(0xFF8B5CF6)
    val MicRecordStart = Color(0xFFEF4444)
    val MicRecordEnd = Color(0xFFF97316)
    val MicProcessStart = Color(0xFF06B6D4)
    val MicProcessEnd = Color(0xFF3B82F6)

    val RingIdle = Color(0xFF8B5CF6)
    val RingRecording = Color(0xFFFCA5A5)
    val RingProcessing = Color(0xFF7DD3FC)

    val WaveformBase = Color(0xFF3F3F5A)
    val WaveformActive = Color(0xFFA78BFA)
    val WaveformPeak = Color(0xFFEC4899)

    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFFB5B5C3)
    val TextTertiary = Color(0xFF6B7280)
    val TextOnAccent = Color(0xFFFFFFFF)

    val Success = Color(0xFF10B981)
    val Error = Color(0xFFEF4444)

    val Surface = Color(0xFF1A1B2E)
    val SurfaceHigh = Color(0xFF252840)
    val BorderSubtle = Color(0xFF2D2F4A)
    val Scrim = Color(0xFF000000).copy(alpha = 0.4f)
}

private object VoiceDimens {
    val MicSize = 88.dp
    val MicIconSize = 38.dp
    val WaveformHeight = 56.dp
    val WaveformBarCount = 28
    val WaveformBarWidth = 3.5.dp
    val WaveformBarGap = 3.dp
    val CardRadius = 20.dp
    val PillRadius = 14.dp
    val ChipRadius = 999.dp
    val StageHorizontalPadding = 24.dp
    val TopBarHeight = 44.dp
    val BottomBarHeight = 52.dp
}

private object VoiceAnimations {
    const val MicPulseMs = 1400
    const val RingExpandMs = 1800
    const val StateTransitionMs = 280
    const val TimerTickMs = 100
}

// ════════════════════════════════════════════════════════════════════════════
//  Entry point
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun VoiceInputLayout(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val voiceInputManager = remember { VoiceInputManager(ctx) }
    val keyboardManager by ctx.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()

    // Provider info snapshot, captured at composition time.
    val providerInfo = remember(voiceInputManager) {
        voiceInputManager.snapshotProviderInfo()
    }
    val openSettings = rememberOpenVoiceSettings()

    // Horizontal swipe offset for visual feedback
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .drawBehind { drawVoiceBackground() }
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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VoiceTopBar(
                provider = providerInfo.first,
                model = providerInfo.second,
                language = providerInfo.third,
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

private fun DrawScope.drawVoiceBackground() {
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to VoiceColors.BgTop,
            0.55f to VoiceColors.BgMid,
            1.0f to VoiceColors.BgBottom,
        ),
    )
    // Subtle radial accent in the upper third to add depth
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to VoiceColors.MicIdleEnd.copy(alpha = 0.10f),
            1.0f to Color.Transparent,
            center = Offset(size.width * 0.5f, size.height * 0.15f),
            radius = size.minDimension * 0.6f,
        ),
        radius = size.minDimension * 0.6f,
        center = Offset(size.width * 0.5f, size.height * 0.15f),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Top bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceTopBar(
    provider: String,
    model: String,
    language: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VoiceDimens.TopBarHeight)
            .padding(horizontal = VoiceDimens.StageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: provider chip
        VoiceChip(
            label = provider,
            leading = "◆",
            accent = VoiceColors.MicIdleEnd,
        )
        Spacer(modifier = Modifier.width(8.dp))
        VoiceChip(
            label = model,
            accent = VoiceColors.WaveformActive,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Right: language + close
        VoiceChip(
            label = language,
            leading = "◌",
            accent = VoiceColors.TextSecondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        VoiceIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Stage router — switches between content states with smooth transitions.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceStage(
    uiState: VoiceInputUiState,
    manager: VoiceInputManager,
) {
    AnimatedContent(
        targetState = uiState.state,
        transitionSpec = {
            (fadeIn(tween(VoiceAnimations.StateTransitionMs)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(VoiceAnimations.StateTransitionMs)))
                .togetherWith(
                    fadeOut(tween(VoiceAnimations.StateTransitionMs)) +
                        scaleOut(targetScale = 1.04f, animationSpec = tween(VoiceAnimations.StateTransitionMs))
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
                onStartRecording = { manager.startRecording() },
                onStopRecording = { manager.stopRecording() },
                onToggleRefinement = { manager.toggleRefinement() },
            )
            VoiceInputState.PROCESSING -> ProcessingStage(
                label = "Transcribing…",
                sublabel = "Sending to ${uiState.providerLabel.ifBlank { "speech-to-text" }}",
                onCancel = { manager.cancel() },
            )
            VoiceInputState.REFINING -> ProcessingStage(
                label = "Refining…",
                sublabel = "Polishing with the language model",
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
            )
            VoiceInputState.ERROR -> ErrorStage(
                message = uiState.errorMessage.ifBlank { "Voice input failed" },
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

// ════════════════════════════════════════════════════════════════════════════
//  Recording / Idle stage — the big mic + waveform + timer + enhance toggle
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecordingStage(
    state: VoiceInputState,
    amplitude: Float,
    amplitudeHistory: List<Float>,
    durationMs: Long,
    refinementEnabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleRefinement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VoiceDimens.StageHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The big mic with concentric pulse rings
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Pulse rings (only while recording)
            if (state == VoiceInputState.RECORDING) {
                PulseRings(amplitude = amplitude)
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

        Spacer(modifier = Modifier.height(20.dp))

        // Live waveform
        VoiceWaveform(
            history = amplitudeHistory,
            isActive = state == VoiceInputState.RECORDING,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Timer
        Text(
            text = formatDuration(durationMs),
            color = if (state == VoiceInputState.RECORDING)
                VoiceColors.TextPrimary else VoiceColors.TextTertiary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status text
        Text(
            text = when (state) {
                VoiceInputState.RECORDING -> "Listening… tap to stop"
                else -> "Tap to start · hold to talk"
            },
            color = VoiceColors.TextSecondary,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Enhance toggle
        EnhanceToggle(
            enabled = refinementEnabled,
            onToggle = onToggleRefinement,
        )
    }
}

@Composable
private fun PulseRings(amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "pulse")
    // Three concentric rings, staggered phases
    for (i in 0 until 3) {
        val phase = i / 3f
        val progress by transition.animateFloat(
            initialValue = phase,
            targetValue = phase + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = VoiceAnimations.RingExpandMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring_$i",
        )
        val normalized = ((progress - phase) * 3f).coerceIn(0f, 1f)
        val ringColor = VoiceColors.RingRecording.copy(alpha = (1f - normalized) * 0.5f)
        val baseRadius = 70f
        val ampBoost = amplitude * 18f
        val radiusDp = (baseRadius + normalized * 90f + ampBoost).dp
        val strokeDp = (3f - normalized * 2f).coerceAtLeast(1f).dp
        Canvas(modifier = Modifier.size(260.dp)) {
            drawCircle(
                color = ringColor,
                radius = radiusDp.toPx(),
                center = center,
                style = Stroke(width = strokeDp.toPx()),
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
    val currentIsRecording by rememberUpdatedState(isRecording)
    var pressed by remember { mutableStateOf(false) }

    val targetScale = if (pressed) 0.94f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mic-press",
    )

    // Subtle breathing in idle
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

    val gradient = Brush.linearGradient(
        colors = if (isRecording)
            listOf(VoiceColors.MicRecordStart, VoiceColors.MicRecordEnd)
        else
            listOf(VoiceColors.MicIdleStart, VoiceColors.MicIdleEnd),
    )

    Box(
        modifier = Modifier
            .size(VoiceDimens.MicSize)
            .scale(scale)
            .then(if (showBreath) Modifier.scale(breathScale) else Modifier)
            .clip(CircleShape)
            .background(gradient)
            .drawBehind {
                // Soft outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to (if (isRecording)
                            VoiceColors.MicRecordStart.copy(alpha = 0.55f)
                        else VoiceColors.MicIdleStart.copy(alpha = 0.45f)),
                        1.0f to Color.Transparent,
                        center = center,
                        radius = size.minDimension * 0.7f,
                    ),
                    radius = size.minDimension * 0.7f,
                    center = center,
                )
            }
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
                    if (up == null) {
                        // gesture cancelled, leave as-is
                        return@awaitEachGesture
                    }
                    val pressDuration = System.currentTimeMillis() - pressStart
                    if (pressDuration < 200L) {
                        // short tap — toggle
                        onTap()
                    } else {
                        // long press — release stops
                        onRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start recording",
            tint = VoiceColors.TextOnAccent,
            modifier = Modifier.size(VoiceDimens.MicIconSize),
        )
    }
}

@Composable
private fun VoiceWaveform(
    history: List<Float>,
    isActive: Boolean,
) {
    val activeColor = VoiceColors.WaveformActive
    val peakColor = VoiceColors.WaveformPeak
    val baseColor = VoiceColors.WaveformBase
    val barCount = VoiceDimens.WaveformBarCount
    val barWidth = VoiceDimens.WaveformBarWidth
    val barGap = VoiceDimens.WaveformBarGap
    val heightDp = VoiceDimens.WaveformHeight

    Canvas(
        modifier = Modifier
            .height(heightDp)
            .fillMaxWidth(),
    ) {
        val totalBars = barCount
        val totalWidth = totalBars * (barWidth.toPx() + barGap.toPx()) - barGap.toPx()
        val startX = (size.width - totalWidth) / 2f
        val midY = size.height / 2f

        // Smooth the history into barCount by averaging
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
            // Curve the amplitude for nicer visual response
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
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(VoiceDimens.ChipRadius))
            .background(VoiceColors.SurfaceHigh)
            .border(1.dp, VoiceColors.BorderSubtle, RoundedCornerShape(VoiceDimens.ChipRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = if (enabled) VoiceColors.WaveformPeak else VoiceColors.TextTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Enhance",
            color = VoiceColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (enabled) VoiceColors.WaveformActive.copy(alpha = 0.25f)
                    else VoiceColors.Surface
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (enabled) "ON" else "OFF",
                color = if (enabled) VoiceColors.WaveformActive else VoiceColors.TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Processing / Refining stage — spinner + cancel
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProcessingStage(
    label: String,
    sublabel: String,
    onCancel: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "process")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "process-sweep",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VoiceDimens.StageHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val ringRadius = (size.minDimension - stroke) / 2f
                val baseColor = VoiceColors.MicProcessEnd.copy(alpha = 0.18f)
                val arcColor = VoiceColors.MicProcessEnd
                drawCircle(
                    color = baseColor,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = stroke),
                )
                val sweepAngle = 90f
                val startAngle = sweep * 360f
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = stroke),
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(VoiceColors.MicProcessEnd),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = label,
            color = VoiceColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sublabel,
            color = VoiceColors.TextTertiary,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(28.dp))

        VoicePillButton(
            label = "Cancel",
            onClick = onCancel,
            kind = VoicePillKind.Secondary,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Success stage — editable transcript + action bar
// ════════════════════════════════════════════════════════════════════════════

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
) {
    val text = uiState.transcribedText
    val hasRawAndRefined = uiState.rawTranscribedText.isNotBlank() && uiState.refinedText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VoiceDimens.StageHorizontalPadding, vertical = 8.dp),
    ) {
        // Header row: title + word count
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.isRefined) "Refined transcript" else "Transcript",
                color = VoiceColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${countWords(text)} words",
                color = VoiceColors.TextTertiary,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Editable transcript card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(VoiceDimens.CardRadius))
                .background(VoiceColors.Surface)
                .border(1.dp, VoiceColors.BorderSubtle, RoundedCornerShape(VoiceDimens.CardRadius))
                .padding(16.dp),
        ) {
            if (text.isBlank()) {
                Text(
                    text = "(empty result)",
                    color = VoiceColors.TextTertiary,
                    fontSize = 15.sp,
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = VoiceColors.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
                    cursorBrush = SolidColor(VoiceColors.WaveformActive),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoicePillButton(
                label = "Redo",
                leading = Icons.Filled.Refresh,
                onClick = onRedo,
                kind = VoicePillKind.Secondary,
                modifier = Modifier.weight(1f),
            )
            if (refinementEnabled && !uiState.isRefined) {
                VoicePillButton(
                    label = "Refine",
                    leading = Icons.Outlined.AutoAwesome,
                    onClick = onRefine,
                    kind = VoicePillKind.Tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasRawAndRefined) {
                VoicePillButton(
                    label = if (uiState.isRefined) "Raw" else "Refined",
                    onClick = onToggleRawRefined,
                    kind = VoicePillKind.Tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            VoicePillButton(
                label = "Insert",
                leading = Icons.Filled.Send,
                onClick = onInsert,
                kind = VoicePillKind.Primary,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Error stage
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorStage(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val showSettingsCta = message.contains("not configured", ignoreCase = true) ||
        message.contains("Settings", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VoiceDimens.StageHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Error icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VoiceColors.Error.copy(alpha = 0.16f))
                .border(1.dp, VoiceColors.Error.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                color = VoiceColors.Error,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Something went wrong",
            color = VoiceColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            color = VoiceColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoicePillButton(
                label = "Dismiss",
                onClick = onDismiss,
                kind = VoicePillKind.Secondary,
            )
            if (showSettingsCta) {
                VoicePillButton(
                    label = "Open Settings",
                    onClick = onOpenSettings,
                    kind = VoicePillKind.Tertiary,
                )
            }
            VoicePillButton(
                label = "Try again",
                onClick = onRetry,
                kind = VoicePillKind.Primary,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Permission stage
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionStage(
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VoiceDimens.StageHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VoiceColors.MicIdleStart.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = VoiceColors.MicIdleStart,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Microphone access needed",
            color = VoiceColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FlorisBoard needs microphone access to transcribe your speech. Audio is sent only to the provider you configured.",
            color = VoiceColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        VoicePillButton(
            label = "Grant permission",
            leading = Icons.Filled.Mic,
            onClick = onRequestPermission,
            kind = VoicePillKind.Primary,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Bottom bar — backspace, enter, paste, ABC, and (when processing) cancel
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceBottomBar(
    keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager,
    state: VoiceInputState,
    onCancelProcessing: () -> Unit,
) {
    val showCancel = state == VoiceInputState.PROCESSING || state == VoiceInputState.REFINING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VoiceDimens.BottomBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Backspace
        BarActionButton(
            contentDescription = "Backspace",
            onClick = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE) },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = null,
                tint = VoiceColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        // Enter
        BarActionButton(
            contentDescription = "Enter",
            onClick = { FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER) },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = VoiceColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        // Paste
        BarActionButton(
            contentDescription = "Paste",
            onClick = { FlorisImeService.performClipboardPaste() },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = null,
                tint = VoiceColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1.5f))
        // ABC — back to text keyboard
        BarActionButton(
            contentDescription = "Switch to keyboard",
            onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
            modifier = Modifier.weight(1.5f).fillMaxHeight().padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = VoiceColors.MicIdleEnd,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ABC",
                color = VoiceColors.MicIdleEnd,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BarActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VoiceColors.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Small primitives — chip, icon button, pill button
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceChip(
    label: String,
    accent: Color,
    leading: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(VoiceDimens.ChipRadius))
            .background(VoiceColors.Surface)
            .border(1.dp, VoiceColors.BorderSubtle, RoundedCornerShape(VoiceDimens.ChipRadius))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                text = leading,
                color = accent,
                fontSize = 10.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = VoiceColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(VoiceColors.Surface)
            .border(1.dp, VoiceColors.BorderSubtle, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = VoiceColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private enum class VoicePillKind { Primary, Secondary, Tertiary }

@Composable
private fun VoicePillButton(
    label: String,
    onClick: () -> Unit,
    kind: VoicePillKind,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (kind) {
        VoicePillKind.Primary -> VoiceColors.Success to VoiceColors.TextOnAccent
        VoicePillKind.Secondary -> VoiceColors.Surface to VoiceColors.TextSecondary
        VoicePillKind.Tertiary -> VoiceColors.SurfaceHigh to VoiceColors.TextPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(VoiceDimens.PillRadius))
            .background(bg)
            .border(1.dp, VoiceColors.BorderSubtle, RoundedCornerShape(VoiceDimens.PillRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (kind == VoicePillKind.Primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

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

@Composable
private fun animateFloatAsState(
    targetValue: Float,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float>,
    label: String,
) = androidx.compose.animation.core.animateFloatAsState(
    targetValue = targetValue,
    animationSpec = animationSpec,
    label = label,
)
