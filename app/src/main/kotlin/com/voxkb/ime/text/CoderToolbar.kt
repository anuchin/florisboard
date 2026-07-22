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

package com.voxkb.ime.text

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voxkb.ime.input.InputEventDispatcher
import com.voxkb.ime.input.InputFeedbackController
import com.voxkb.ime.input.LocalInputFeedbackController
import com.voxkb.ime.keyboard.VoxKBImeSizing
import com.voxkb.ime.keyboard.KeyData
import com.voxkb.ime.text.key.KeyCode
import com.voxkb.ime.text.key.KeyType
import com.voxkb.ime.text.keyboard.TextKeyData
import com.voxkb.ime.theme.VoxKBImeUi
import com.voxkb.keyboardManager
import com.voxkb.lib.snygg.SnyggSelector
import com.voxkb.lib.snygg.ui.SnyggBox
import com.voxkb.lib.snygg.ui.SnyggColumn
import com.voxkb.lib.snygg.ui.SnyggRow
import com.voxkb.lib.snygg.ui.SnyggText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CODER_SYMBOLS = listOf(
    '(', ')', '[', ']', '{', '}', '<', '>', '|', '&', ';', '=', '/', '\\', '-', '+', '*', '%', '#', '@', '~',
)

@Composable
fun CoderToolbar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val inputEventDispatcher = keyboardManager.inputEventDispatcher
    val inputFeedbackController = LocalInputFeedbackController.current

    var ctrlActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    // Release any sticky modifiers that are still held down when the toolbar
    // leaves composition. Without this, the InputEventDispatcher would keep
    // thinking Ctrl/Shift/Alt is pressed after the toolbar is hidden, causing
    // the next key press to be sent as a modified key (e.g. Ctrl+C instead of C).
    DisposableEffect(Unit) {
        onDispose {
            inputEventDispatcher.sendUp(TextKeyData.CTRL)
            inputEventDispatcher.sendUp(TextKeyData.SHIFT)
            inputEventDispatcher.sendUp(TextKeyData.ALT)
        }
    }

    SnyggColumn(
        elementName = VoxKBImeUi.CoderToolbarRow.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Main row: Esc, Tab, Ctrl, Shift, Alt, arrows, Del
        SnyggRow(
            elementName = VoxKBImeUi.CoderToolbarRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(VoxKBImeSizing.keyboardRowBaseHeight * 0.7f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Esc
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData(type = KeyType.FUNCTION, code = KeyCode.ESCAPE, label = "Esc"),
                label = "Esc",
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Tab
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData(type = KeyType.FUNCTION, code = KeyCode.TAB, label = "Tab"),
                label = "Tab",
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Ctrl (sticky)
            StickyModifierButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.CTRL,
                label = "Ctrl",
                isActive = ctrlActive,
                onToggle = {
                    if (ctrlActive) {
                        inputEventDispatcher.sendUp(TextKeyData.CTRL)
                    } else {
                        inputEventDispatcher.sendDown(TextKeyData.CTRL)
                    }
                    ctrlActive = !ctrlActive
                },
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Shift (sticky)
            StickyModifierButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.SHIFT,
                label = "Shift",
                isActive = shiftActive,
                onToggle = {
                    if (shiftActive) {
                        inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                    } else {
                        inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                    }
                    shiftActive = !shiftActive
                },
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Alt (sticky)
            StickyModifierButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.ALT,
                label = "Alt",
                isActive = altActive,
                onToggle = {
                    if (altActive) {
                        inputEventDispatcher.sendUp(TextKeyData.ALT)
                    } else {
                        inputEventDispatcher.sendDown(TextKeyData.ALT)
                    }
                    altActive = !altActive
                },
            )

            // Flexible space
            Spacer(modifier = Modifier.weight(1f))

            // Arrow keys (repeatable — press and hold to repeat)
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.ARROW_LEFT,
                label = "\u2190",
                repeatable = true,
            )
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.ARROW_UP,
                label = "\u2191",
                repeatable = true,
            )
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.ARROW_DOWN,
                label = "\u2193",
                repeatable = true,
            )
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.ARROW_RIGHT,
                label = "\u2192",
                repeatable = true,
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Del (repeatable)
            CoderToolbarButton(
                inputEventDispatcher = inputEventDispatcher,
                inputFeedbackController = inputFeedbackController,
                keyData = TextKeyData.FORWARD_DELETE,
                label = "Del",
                repeatable = true,
            )
        }

        // Symbols row: horizontally scrollable coder symbols
        SnyggRow(
            elementName = VoxKBImeUi.CoderToolbarRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(VoxKBImeSizing.keyboardRowBaseHeight * 0.55f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (symbol in CODER_SYMBOLS) {
                CoderSymbolButton(
                    inputEventDispatcher = inputEventDispatcher,
                    inputFeedbackController = inputFeedbackController,
                    symbol = symbol,
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun RowScope.CoderToolbarButton(
    inputEventDispatcher: InputEventDispatcher,
    inputFeedbackController: InputFeedbackController,
    keyData: KeyData,
    label: String,
    repeatable: Boolean = false,
) {
    var isPressed by remember { mutableStateOf(false) }
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    SnyggBox(
        elementName = VoxKBImeUi.CoderToolbarButton.elementName,
        attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code),
        selector = selector,
        clickAndSemanticsModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false).also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    isPressed = true
                    inputFeedbackController.keyPress(keyData)
                    inputEventDispatcher.sendDown(keyData)
                    // For repeatable keys, start a repeating timer after the initial press.
                    repeatJob?.cancel()
                    if (repeatable) {
                        repeatJob = scope.launch {
                            delay(400)
                            inputEventDispatcher.sendUp(keyData)
                            inputEventDispatcher.sendDownUp(keyData)
                            while (true) {
                                delay(50)
                                inputEventDispatcher.sendDownUp(keyData)
                            }
                        }
                    }
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    repeatJob?.cancel()
                    repeatJob = null
                    if (up != null) {
                        inputEventDispatcher.sendUp(keyData)
                    } else {
                        inputEventDispatcher.sendCancel(keyData)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(
            elementName = VoxKBImeUi.CoderToolbarButton.elementName,
            attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code),
            text = label,
        )
    }
}

@Composable
private fun RowScope.CoderSymbolButton(
    inputEventDispatcher: InputEventDispatcher,
    inputFeedbackController: InputFeedbackController,
    symbol: Char,
) {
    var isPressed by remember { mutableStateOf(false) }
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE
    val keyData = remember(symbol) {
        TextKeyData(type = KeyType.CHARACTER, code = symbol.code, label = symbol.toString())
    }

    SnyggBox(
        elementName = VoxKBImeUi.CoderToolbarButton.elementName,
        attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code),
        selector = selector,
        clickAndSemanticsModifier = Modifier
            .padding(horizontal = 1.dp)
            .size(width = 36.dp, height = VoxKBImeSizing.keyboardRowBaseHeight * 0.5f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false).also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    isPressed = true
                    inputFeedbackController.keyPress(keyData)
                    inputEventDispatcher.sendDownUp(keyData)
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up == null) {
                        inputEventDispatcher.sendCancel(keyData)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(
            elementName = VoxKBImeUi.CoderToolbarButton.elementName,
            attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code),
            text = symbol.toString(),
        )
    }
}

@Composable
private fun RowScope.StickyModifierButton(
    inputEventDispatcher: InputEventDispatcher,
    inputFeedbackController: InputFeedbackController,
    keyData: KeyData,
    label: String,
    isActive: Boolean,
    onToggle: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    // Active sticky modifiers use a distinct "active" attribute so the theme can
    // style them differently from a momentary press (PRESSED selector).
    val activeAttr = if (isActive)
        mapOf(VoxKBImeUi.Attr.VoiceState to listOf("active")) else emptyMap()
    val selector = if (isPressed && !isActive) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggBox(
        elementName = VoxKBImeUi.CoderToolbarButton.elementName,
        attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code) + activeAttr,
        selector = selector,
        clickAndSemanticsModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false).also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    isPressed = true
                    inputFeedbackController.keyPress(keyData)
                    onToggle()
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up == null) {
                        // gesture cancelled — nothing to revert for a sticky toggle
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(
            elementName = VoxKBImeUi.CoderToolbarButton.elementName,
            attributes = mapOf(VoxKBImeUi.Attr.Code to keyData.code) + activeAttr,
            text = label,
        )
    }
}
