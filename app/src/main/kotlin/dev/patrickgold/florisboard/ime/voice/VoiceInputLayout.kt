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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.media.KeyboardLikeButton
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow

private val PunctuationButtons = listOf(".", ",", "?", "!", ";", ":", "-", "(", ")")

@Composable
fun VoiceInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceInputManager = remember { VoiceInputManager(context) }
    val keyboardManager by context.keyboardManager()
    val uiState by voiceInputManager.uiState.collectAsState()

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val swipeThreshold = 100f
                    if (dragAmount.x < -swipeThreshold) {
                        // Swipe left -> back to text keyboard
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    }
                }
            },
    ) {
        // Main content area (status + transcript)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.state) {
                VoiceInputState.IDLE -> IdleContent(
                    onStartRecording = { voiceInputManager.startRecording() },
                    onStopRecording = { voiceInputManager.stopRecording() },
                )
                VoiceInputState.RECORDING -> RecordingContent(
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

        // Punctuation strip
        PunctuationStrip(keyboardManager = keyboardManager)

        // Bottom action row: undo, redo, backspace, paste | mic | ABC
        ActionRow(
            keyboardManager = keyboardManager,
            voiceInputManager = voiceInputManager,
            uiState = uiState,
        )
    }
}

@Composable
private fun PunctuationStrip(keyboardManager: KeyboardManager) {
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardRowBaseHeight * 0.55f),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PunctuationButtons.forEach { symbol ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            FlorisImeService.commitText(symbol)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        // Newline button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    keyboardManager: KeyboardManager,
    voiceInputManager: VoiceInputManager,
    uiState: VoiceInputUiState,
) {
    SnyggRow(
        elementName = FlorisImeUi.MediaBottomRow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Undo
        IconButton(
            onClick = { FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_Z, metaCtrl = true) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = "Undo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // Redo
        IconButton(
            onClick = { FlorisImeService.sendDownAndUpKeyEvent(KeyEvent.KEYCODE_Z, metaCtrl = true, metaShift = true) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = "Redo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // Backspace
        KeyboardLikeButton(
            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
            inputEventDispatcher = keyboardManager.inputEventDispatcher,
            keyData = TextKeyData.DELETE,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Text("⌫", fontSize = 18.sp)
        }
        // Paste
        IconButton(
            onClick = { FlorisImeService.performClipboardPaste() },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = "Paste",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Mic button (hold-to-record or tap-to-toggle)
        MicButton(
            uiState = uiState,
            onStartRecording = { voiceInputManager.startRecording() },
            onStopRecording = { voiceInputManager.stopRecording() },
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
        )

        Spacer(modifier = Modifier.width(4.dp))

        // ABC button to switch back to keyboard
        KeyboardLikeButton(
            elementName = FlorisImeUi.MediaBottomRowButton.elementName,
            inputEventDispatcher = keyboardManager.inputEventDispatcher,
            keyData = TextKeyData.IME_UI_MODE_TEXT,
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "ABC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun MicButton(
    uiState: VoiceInputUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = uiState.state == VoiceInputState.RECORDING

    val backgroundColor = when {
        isRecording -> MaterialTheme.colorScheme.error
        uiState.state == VoiceInputState.IDLE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isRecording || uiState.state == VoiceInputState.IDLE -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Hold-to-record: start on press, stop on release
                        onStartRecording()
                        val released = tryAwaitRelease()
                        if (released) {
                            onStopRecording()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop" else "Record",
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun IdleContent(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hold mic to record, or tap",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Swipe left to return to keyboard",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingContent(
    amplitude: Float,
    onStopRecording: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
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
            val ringRadius = (40f + amplitude * 25f).dp
            val ringRadiusPx = with(LocalDensity.current) { ringRadius.toPx() }
            val ringColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            val strokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(
                    color = ringColor,
                    radius = ringRadiusPx,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun ProcessingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Transcribing...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SnyggBox(
            elementName = FlorisImeUi.Media.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = transcribedText.ifBlank { "(empty result)" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                maxLines = 4,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 13.sp)
            }
            TextButton(onClick = onRecordAgain) {
                Text("Redo", fontSize = 13.sp)
            }
            TextButton(onClick = onInsert) {
                Text("Insert", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

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
