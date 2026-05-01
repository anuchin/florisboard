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
import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.media.KeyboardLikeButton
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow

@Composable
fun VoiceInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceInputManager = remember { VoiceInputManager(context) }
    val keyboardManager by context.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()

    // Horizontal swipe offset for visual feedback
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            // Horizontal drag for swipe-to-keyboard, only on edges / empty areas
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffsetX < -150f) {
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-300f, 0f)
                }
            }
            .graphicsLayer { translationX = swipeOffsetX },
    ) {
        // Main area — mic button centered + status text
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.state) {
                VoiceInputState.IDLE -> IdleMicContent(
                    onStartRecording = { voiceInputManager.startRecording() },
                    onStopRecording = { voiceInputManager.stopRecording() },
                )
                VoiceInputState.RECORDING -> RecordingMicContent(
                    amplitude = uiState.amplitude,
                    onStopRecording = { voiceInputManager.stopRecording() },
                )
                VoiceInputState.PROCESSING -> ProcessingContent()
                VoiceInputState.SUCCESS -> SuccessContent(
                    transcribedText = uiState.transcribedText,
                    onInsert = { voiceInputManager.commitText() },
                    onDismiss = { voiceInputManager.reset() },
                    onRecordAgain = { voiceInputManager.startRecording() },
                )
                VoiceInputState.ERROR -> ErrorContent(
                    errorMessage = uiState.errorMessage,
                    onRetry = { voiceInputManager.startRecording() },
                )
                VoiceInputState.PERMISSION_REQUIRED -> PermissionRequiredContent()
            }
        }

        // Bottom row: Backspace | Enter | Paste ... ABC
        BottomRow(keyboardManager = keyboardManager)
    }
}

@Composable
private fun BottomRow(keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager) {
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Backspace
        KeyboardLikeButton(
            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
            inputEventDispatcher = keyboardManager.inputEventDispatcher,
            keyData = TextKeyData.DELETE,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Text("⌫", fontSize = 18.sp)
        }
        // Enter
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures {
                        FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_ENTER)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardReturn,
                contentDescription = "Enter",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        // Paste
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures {
                        FlorisImeService.performClipboardPaste()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = "Paste",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1.5f))

        // ABC — back to keyboard
        KeyboardLikeButton(
            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
            inputEventDispatcher = keyboardManager.inputEventDispatcher,
            keyData = TextKeyData.IME_UI_MODE_TEXT,
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
        ) {
            Text(text = "ABC", fontWeight = FontWeight.Bold)
        }
    }
}

// ── IDLE: Big centered mic, hold-to-record ──

@Composable
private fun IdleMicContent(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Record",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Hold to speak",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

// ── RECORDING: Big mic with waveform, hold to continue ──

@Composable
private fun RecordingMicContent(
    amplitude: Float,
    onStopRecording: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val ringRadius = (45f + amplitude * 25f).dp
            val ringRadiusPx = with(LocalDensity.current) { ringRadius.toPx() }
            val ringColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            val strokeWidth = with(LocalDensity.current) { 4.dp.toPx() }
            Canvas(modifier = Modifier.size(140.dp)) {
                drawCircle(
                    color = ringColor,
                    radius = ringRadiusPx,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Listening...",
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Release to stop",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}

// ── PROCESSING ──

@Composable
private fun ProcessingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Transcribing...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

// ── SUCCESS ──

@Composable
private fun SuccessContent(
    transcribedText: String,
    onInsert: () -> Unit,
    onDismiss: () -> Unit,
    onRecordAgain: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SnyggBox(
            elementName = FlorisImeUi.Media.elementName,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = transcribedText.ifBlank { "(empty result)" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                maxLines = 5,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 13.sp) }
            TextButton(onClick = onRecordAgain) { Text("Redo", fontSize = 13.sp) }
            TextButton(onClick = onInsert) {
                Text("Insert", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ── ERROR ──

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = errorMessage.ifBlank { "Voice input failed" },
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}

// ── PERMISSION ──

@Composable
private fun PermissionRequiredContent() {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Microphone access is required for voice input.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = {
            val intent = Intent(context, FlorisAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                action = "REQUEST_RECORD_AUDIO"
            }
            context.startActivity(intent)
        }) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
    }
}
