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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggRow

@Composable
fun CoderToolbar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val inputEventDispatcher = keyboardManager.inputEventDispatcher
    val inputFeedbackController = LocalInputFeedbackController.current

    var ctrlActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    SnyggRow(
        elementName = FlorisImeUi.CoderToolbarRow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardRowBaseHeight * 0.7f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Esc
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData(type = KeyType.CHARACTER, code = KeyCode.ESCAPE, label = "Esc"),
            label = "Esc",
        )
        Spacer(modifier = Modifier.width(2.dp))
        // Tab
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData(type = KeyType.CHARACTER, code = KeyCode.TAB, label = "Tab"),
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

        // Arrow keys
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData.ARROW_LEFT,
            label = "\u2190",
        )
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData.ARROW_UP,
            label = "\u2191",
        )
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData.ARROW_DOWN,
            label = "\u2193",
        )
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData.ARROW_RIGHT,
            label = "\u2192",
        )
        Spacer(modifier = Modifier.width(2.dp))
        // Del
        CoderToolbarButton(
            inputEventDispatcher = inputEventDispatcher,
            inputFeedbackController = inputFeedbackController,
            keyData = TextKeyData.FORWARD_DELETE,
            label = "Del",
        )
    }
}

@Composable
private fun RowScope.CoderToolbarButton(
    inputEventDispatcher: InputEventDispatcher,
    inputFeedbackController: dev.patrickgold.florisboard.ime.input.InputFeedbackController,
    keyData: KeyData,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggBox(
        elementName = FlorisImeUi.CoderToolbarButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to keyData.code),
        selector = selector,
        clickAndSemanticsModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .indication(interactionSource, ripple())
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false).also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    inputFeedbackController.keyPress(keyData)
                    inputEventDispatcher.sendDown(keyData)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        inputEventDispatcher.sendUp(keyData)
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                        inputEventDispatcher.sendCancel(keyData)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RowScope.StickyModifierButton(
    inputEventDispatcher: InputEventDispatcher,
    inputFeedbackController: dev.patrickgold.florisboard.ime.input.InputFeedbackController,
    keyData: KeyData,
    label: String,
    isActive: Boolean,
    onToggle: () -> Unit,
) {
    val selector = if (isActive) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggBox(
        elementName = FlorisImeUi.CoderToolbarButton.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to keyData.code),
        selector = selector,
        clickAndSemanticsModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures {
                    inputFeedbackController.keyPress(keyData)
                    onToggle()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
