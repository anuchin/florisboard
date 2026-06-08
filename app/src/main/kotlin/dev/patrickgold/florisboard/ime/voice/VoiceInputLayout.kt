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
import androidx.compose.animation.animateFloatAsState
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState

// ════════════════════════════════════════════════════════════════════════════
//  Animation constants
// ════════════════════════════════════════════════════════════════════════════

private const val STATE_TRANSITION_MS = 280
private const val RING_EXPAND_MS = 1800
private const val WAVEFORM_BAR_COUNT = 28


// ════════════════════════════════════════════════════════════════════════════
//  Entry point — wraps content in a dynamic M3 theme using the app's accent
// ════════════════════════════════════════════════════════════════════════════

private val DEFAULT_SEED = Color(0xFF4CAF50)

@Composable
fun VoiceInputLayout(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val accentColor by prefs.theme.accentColor.collectAsState()
    val isDark = isSystemInDarkTheme()

    val seedColor = if (accentColor.isUnspecified) DEFAULT_SEED else accentColor
    val colorScheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        isAmoled = false,
        style = PaletteStyle.Neutral,
    )
    MaterialTheme(colorScheme = colorScheme) {
        VoiceInputLayoutContent(modifier)
    }
}

@Composable
private fun VoiceInputLayoutContent(modifier: Modifier) {
    val ctx = LocalContext.current
    val voiceInputManager = remember { VoiceInputManager(ctx) }
    val keyboardManager by ctx.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()

    val providerInfo = remember(voiceInputManager) {
        voiceInputManager.snapshotProviderInfo()
    }
    val openSettings = rememberOpenVoiceSettings()

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    val colorScheme = MaterialTheme.colorScheme
    val bgTop = colorScheme.surfaceContainerLowest
    val bgMid = colorScheme.surfaceContainerLow
    val bgBottom = colorScheme.surfaceContainer
    val accentGlow = colorScheme.primary.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to bgTop,
                        0.55f to bgMid,
                        1.0f to bgBottom,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to accentGlow,
                        1.0f to Color.Transparent,
                        center = Offset(size.width * 0.5f, size.height * 0.15f),
                        radius = size.minDimension * 0.6f,
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                )
            }
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
                    openSettings = openSettings,
                    ctx = ctx,
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
    val closeDesc = stringResource(R.string.voice__close)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceChip(label = provider)
        Spacer(modifier = Modifier.width(8.dp))
        VoiceChip(label = model)

        Spacer(modifier = Modifier.weight(1f))

        VoiceChip(label = language)
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(32.dp)
                .semantics { contentDescription = closeDesc },
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Stage router — switches between content states with smooth transitions
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceStage(
    uiState: VoiceInputUiState,
    manager: VoiceInputManager,
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
                amplitude = uiState.amplitude,
                amplitudeHistory = uiState.amplitudeHistory,
                durationMs = uiState.durationMs,
                refinementEnabled = manager.isRefinementEnabled(),
                onStartRecording = { manager.startRecording() },
                onStopRecording = { manager.stopRecording() },
                onToggleRefinement = { manager.toggleRefinement() },
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
                label = stringResource(R.string.voice__refining),
                sublabel = stringResource(R.string.voice__refining_sublabel),
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
    val colorScheme = MaterialTheme.colorScheme
    val timerDesc = stringResource(R.string.voice__listening)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
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

        VoiceWaveform(
            history = amplitudeHistory,
            isActive = state == VoiceInputState.RECORDING,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.headlineSmall,
            color = if (state == VoiceInputState.RECORDING)
                colorScheme.onSurface else colorScheme.outline,
            letterSpacing = 1.5.sp,
            modifier = Modifier.semantics {
                contentDescription = timerDesc
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (state) {
                VoiceInputState.RECORDING -> stringResource(R.string.voice__listening)
                else -> stringResource(R.string.voice__tap_to_start)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        EnhanceToggle(
            enabled = refinementEnabled,
            onToggle = onToggleRefinement,
        )
    }
}

@Composable
private fun PulseRings(amplitude: Float) {
    val ringColor = MaterialTheme.colorScheme.error
    val transition = rememberInfiniteTransition(label = "pulse")
    for (i in 0 until 3) {
        val phase = i / 3f
        val progress by transition.animateFloat(
            initialValue = phase,
            targetValue = phase + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = RING_EXPAND_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring_$i",
        )
        val normalized = ((progress - phase) * 3f).coerceIn(0f, 1f)
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
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeDp.toPx()),
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
    val colorScheme = MaterialTheme.colorScheme
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

    val idleGradient = Brush.linearGradient(
        colors = listOf(colorScheme.primary, colorScheme.primaryContainer),
    )
    val recordGradient = Brush.linearGradient(
        colors = listOf(colorScheme.error, colorScheme.errorContainer),
    )
    val gradient = if (isRecording) recordGradient else idleGradient

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
                drawCircle(brush = gradient)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to (if (isRecording)
                            colorScheme.error.copy(alpha = 0.55f)
                        else colorScheme.primary.copy(alpha = 0.45f)),
                        1.0f to Color.Transparent,
                        center = center,
                        radius = size.minDimension * 0.7f,
                    ),
                    radius = size.minDimension * 0.7f,
                    center = center,
                )
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
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun VoiceWaveform(
    history: List<Float>,
    isActive: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeColor = colorScheme.primary
    val peakColor = colorScheme.tertiary
    val baseColor = colorScheme.surfaceVariant
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
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = enabled,
        onClick = onToggle,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.voice__enhance),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (enabled)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = if (enabled) stringResource(R.string.voice__on)
                               else stringResource(R.string.voice__off),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        },
    )
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
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            color = colorScheme.primary,
            strokeWidth = 5.dp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.outline,
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.voice__cancel))
        }
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
    val colorScheme = MaterialTheme.colorScheme
    val text = uiState.transcribedText
    val hasRawAndRefined = uiState.rawTranscribedText.isNotBlank() && uiState.refinedText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.isRefined) stringResource(R.string.voice__refined_transcript)
                       else stringResource(R.string.voice__transcript),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.voice__word_count, countWords(text)),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (text.isBlank()) {
                    Text(
                        text = stringResource(R.string.voice__empty_result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.outline,
                    )
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onRedo,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.voice__redo))
            }
            if (refinementEnabled && !uiState.isRefined) {
                FilledTonalButton(
                    onClick = onRefine,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.voice__refine))
                }
            }
            if (hasRawAndRefined) {
                FilledTonalButton(
                    onClick = onToggleRawRefined,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (uiState.isRefined) stringResource(R.string.voice__raw)
                        else stringResource(R.string.voice__refined)
                    )
                }
            }
            Button(
                onClick = onInsert,
                modifier = Modifier.weight(1.2f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.voice__insert),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
    val colorScheme = MaterialTheme.colorScheme
    val showSettingsCta = message.contains("not configured", ignoreCase = true) ||
        message.contains("Settings", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = colorScheme.errorContainer,
            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.4f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.voice__error_title),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.voice__dismiss))
            }
            if (showSettingsCta) {
                FilledTonalButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.voice__open_settings))
                }
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.voice__try_again))
            }
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
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.voice__permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.voice__permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(onClick = onRequestPermission) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.voice__grant_permission))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Bottom bar — backspace, enter, paste, ABC
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceBottomBar(
    keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager,
    state: VoiceInputState,
    onCancelProcessing: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backspaceDesc = stringResource(R.string.voice__backspace)
    val enterDesc = stringResource(R.string.voice__enter)
    val pasteDesc = stringResource(R.string.voice__paste)
    val switchDesc = stringResource(R.string.voice__switch_keyboard)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarActionButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE) },
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = backspaceDesc,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        BarActionButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = { FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER) },
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = enterDesc,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        BarActionButton(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp),
            onClick = { FlorisImeService.performClipboardPaste() },
        ) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = pasteDesc,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1.5f))
        BarActionButton(
            modifier = Modifier.weight(1.5f).fillMaxHeight().padding(4.dp),
            onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = switchDesc,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.voice__abc),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BarActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        content()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Small primitives — display chip
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceChip(label: String) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        enabled = false,
        shape = RoundedCornerShape(999.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
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
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}
