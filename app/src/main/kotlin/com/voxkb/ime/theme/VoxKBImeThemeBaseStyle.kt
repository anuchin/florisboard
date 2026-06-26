/*
 * Copyright (C) 2022-2025 The VoxKB Contributors
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

package com.voxkb.ime.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxkb.ime.input.InputShiftState
import com.voxkb.ime.text.key.KeyCode
import com.voxkb.lib.snygg.SnyggSelector
import com.voxkb.lib.snygg.SnyggStylesheet

val VoxKBImeThemeBaseStyle = SnyggStylesheet.v2 {
    defines {
        // Material You color roles. Because every rule below references these via
        // var(--...), swapping the @defines block here makes the entire default
        // keyboard theme follow the system Material You scheme (light/dark resolved
        // automatically against the dynamic color scheme provided by ProvideSnyggTheme).
        "--primary" to dynamicDarkColor("primary")
        "--primary-variant" to dynamicDarkColor("primaryContainer")
        "--secondary" to dynamicDarkColor("tertiary")
        "--secondary-variant" to dynamicDarkColor("tertiaryContainer")
        "--background" to dynamicDarkColor("surface")
        "--background-variant" to dynamicDarkColor("surfaceContainerLow")
        "--surface" to dynamicDarkColor("surfaceContainer")
        "--surface-variant" to dynamicDarkColor("surfaceContainerHigh")

        "--on-primary" to dynamicDarkColor("onPrimary")
        "--on-background" to dynamicDarkColor("onBackground")
        "--on-background-disabled" to dynamicDarkColor("outlineVariant")
        "--on-surface" to dynamicDarkColor("onSurface")
        "--error" to dynamicDarkColor("error")
        "--on-error" to dynamicDarkColor("onError")
        "--error-container" to dynamicDarkColor("errorContainer")
        "--on-error-container" to dynamicDarkColor("onErrorContainer")
        "--warning" to dynamicDarkColor("tertiary")
        "--voice-surface" to dynamicDarkColor("surfaceContainer")
        "--voice-surface-variant" to dynamicDarkColor("surfaceContainerHigh")

        "--shape" to roundedCornerShape(12.dp)
        "--shape-variant" to roundedCornerShape(16.dp)
    }

    VoxKBImeUi.Window.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    VoxKBImeUi.Key.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
    }
    VoxKBImeUi.Key.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface-variant")
        foreground = `var`("--on-surface")
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.ENTER)) {
        background = `var`("--primary")
        foreground = `var`("--on-surface")
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.ENTER), selector = SnyggSelector.PRESSED) {
        background = `var`("--primary-variant")
        foreground = `var`("--on-surface")
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.SPACE)) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(12.sp)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2,
    )) {
        fontSize = fontSize(18.sp)
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED,
    )) {
        fontSize = fontSize(12.sp)
    }
    VoxKBImeUi.Key.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.VIEW_NUMERIC_ADVANCED)) {
        textMaxLines = textMaxLines(2)
    }
    VoxKBImeUi.Key.elementName(
        VoxKBImeUi.Attr.Code to listOf(KeyCode.SHIFT),
        VoxKBImeUi.Attr.ShiftState to listOf(InputShiftState.CAPS_LOCK.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    VoxKBImeUi.KeyHint.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        fontFamily = genericFontFamily(FontFamily.Monospace)
        fontSize = fontSize(12.sp)
        padding = padding(0.dp, 1.dp, 1.dp, 0.dp)
        textMaxLines = textMaxLines(1)
    }
    VoxKBImeUi.KeyPopupBox.elementName {
        background = rgbaColor(117, 117, 117)
        foreground = `var`("--on-surface")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
        shadowElevation = size(2.dp)
    }
    VoxKBImeUi.KeyPopupElement.elementName(selector = SnyggSelector.FOCUS) {
        background = rgbaColor(189, 189, 189)
        shape = `var`("--shape")
    }
    VoxKBImeUi.KeyPopupExtendedIndicator.elementName {
        fontSize = fontSize(16.sp)
    }

    VoxKBImeUi.Smartbar.elementName {
        fontSize = fontSize(18.sp)
    }
    VoxKBImeUi.SmartbarSharedActionsToggle.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        margin = padding(6.dp)
        shape = circleShape()
        shadowElevation = size(2.dp)
    }
    VoxKBImeUi.SmartbarExtendedActionsToggle.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        margin = padding(6.dp)
        shape = circleShape()
    }
    VoxKBImeUi.SmartbarActionKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        shape = `var`("--shape")
    }
    VoxKBImeUi.SmartbarActionKey.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }

    VoxKBImeUi.SmartbarActionsOverflow.elementName {
        margin = padding(4.dp)
    }
    VoxKBImeUi.SmartbarActionsOverflowCustomizeButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        fontSize = fontSize(14.sp)
        margin = padding(0.dp, 8.dp, 0.dp, 0.dp)
        shape = roundedCornerShape(24.dp)
    }
    VoxKBImeUi.SmartbarActionTile.elementName {
        background = `var`("--background-variant")
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(4.dp)
        shape = roundedCornerShape(20)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarActionTile.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }
    VoxKBImeUi.SmartbarActionTileIcon.elementName {
        fontSize = fontSize(24.sp)
        margin = padding(0.dp, 0.dp, 0.dp, 8.dp)
    }

    VoxKBImeUi.SmartbarActionsEditor.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    VoxKBImeUi.SmartbarActionsEditorHeader.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(16.sp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarActionsEditorHeaderButton.elementName {
        margin = padding(4.dp)
        shape = circleShape()
    }
    VoxKBImeUi.SmartbarActionsEditorSubheader.elementName {
        foreground = `var`("--secondary")
        fontSize = fontSize(16.sp)
        fontWeight = fontWeight(FontWeight.Bold)
        padding = padding(12.dp, 16.dp, 12.dp, 8.dp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarActionsEditorTileGrid.elementName {
        margin = padding(4.dp, 0.dp)
    }
    VoxKBImeUi.SmartbarActionsEditorTile.elementName {
        margin = padding(4.dp)
        padding = padding(8.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarActionsEditorTile.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.NOOP)) {
        foreground = `var`("--on-background-disabled")
    }
    VoxKBImeUi.SmartbarActionsEditorTile.elementName(VoxKBImeUi.Attr.Code to listOf(KeyCode.DRAG_MARKER)) {
        foreground = rgbaColor(255, 0, 0)
    }

    VoxKBImeUi.SmartbarCandidateWord.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarCandidateWord.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    VoxKBImeUi.SmartbarCandidateWordSecondaryText.elementName {
        fontSize = fontSize(8.sp)
        margin = padding(0.dp, 2.dp, 0.dp, 0.dp)
    }
    VoxKBImeUi.SmartbarCandidateClip.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SmartbarCandidateClip.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    VoxKBImeUi.SmartbarCandidateClipIcon.elementName {
        margin = padding(0.dp, 0.dp, 4.dp, 0.dp)
    }
    VoxKBImeUi.SmartbarCandidateSpacer.elementName {
        foreground = `var`("--on-background-disabled")
    }

    VoxKBImeUi.ClipboardHeader.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(16.sp)
    }
    VoxKBImeUi.ClipboardSubheader.elementName {
        fontSize = fontSize(14.sp)
        margin = padding(6.dp)
    }
    VoxKBImeUi.ClipboardContent.elementName {
        padding = padding(10.dp)
    }
    VoxKBImeUi.ClipboardItem.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
        textMaxLines = textMaxLines(10)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.ClipboardItemPopup.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
    }
    VoxKBImeUi.ClipboardItemActions.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        margin = padding(4.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(2.dp)
    }
    VoxKBImeUi.ClipboardItemAction.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(12.dp)
    }
    VoxKBImeUi.ClipboardItemActionText.elementName {
        margin = padding(8.dp, 0.dp, 0.dp, 0.dp)
    }
    VoxKBImeUi.ClipboardHistoryDisabledButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        shape = roundedCornerShape(24.dp)
    }

    VoxKBImeUi.MediaEmojiKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
    }
    VoxKBImeUi.MediaEmojiKey.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    VoxKBImeUi.GlideTrail.elementName {
        foreground = `var`("--primary")
    }

    VoxKBImeUi.InlineAutofillChip.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    VoxKBImeUi.Media.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    VoxKBImeUi.MediaBottomRow.elementName {
        background = `var`("--background")
    }

    VoxKBImeUi.MediaBottomRowButton.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        shape = `var`("--shape")
    }

    VoxKBImeUi.IncognitoModeIndicator.elementName {
        foreground = rgbaColor(255, 255, 255, 0.067f)
    }

    VoxKBImeUi.CoderToolbarRow.elementName {
        background = `var`("--background-variant")
    }

    VoxKBImeUi.CoderToolbarButton.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(13.sp)
        shape = `var`("--shape")
    }
    VoxKBImeUi.CoderToolbarButton.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }

    VoxKBImeUi.OneHandedPanel.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }

    VoxKBImeUi.SubtypePanel.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    VoxKBImeUi.SubtypePanelHeader.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(18.sp)
        padding = padding(12.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    VoxKBImeUi.SubtypePanelListItem.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(16.dp)
    }
    VoxKBImeUi.SubtypePanelListItemIconLeading.elementName {
        fontSize = fontSize(24.sp)
        padding = padding(0.dp, 0.dp, 16.dp, 0.dp)
    }
    VoxKBImeUi.SubtypePanelListItemText.elementName {
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }

    VoxKBImeUi.VoiceInputRoot.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    VoxKBImeUi.VoiceTopBar.elementName {
        // M3 surfaceContainer-style tonal layer for the header band.
        background = `var`("--voice-surface")
        foreground = `var`("--on-background")
        padding = padding(0.dp, 0.dp, 0.dp, 0.dp)
    }

    VoxKBImeUi.VoiceChip.elementName {
        // M3 FilterChip (unselected) — outlined-ish neutral pill.
        background = `var`("--voice-surface-variant")
        foreground = `var`("--on-background")
        fontSize = fontSize(12.sp)
        padding = padding(6.dp, 12.dp, 6.dp, 12.dp)
        shape = roundedCornerShape(50)
        textMaxLines = textMaxLines(1)
    }
    VoxKBImeUi.VoiceChip.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("selected"),
    ) {
        // M3 FilterChip selected — primary tonal fill.
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }

    VoxKBImeUi.VoiceChipText.elementName {
        fontSize = fontSize(12.sp)
        fontWeight = fontWeight(FontWeight.Medium)
    }

    VoxKBImeUi.VoiceMicButton.elementName {
        // Elevated FAB-style focal point.
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        shape = circleShape()
        shadowElevation = size(8.dp)
    }
    VoxKBImeUi.VoiceMicButton.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("recording"),
    ) {
        background = `var`("--error")
        foreground = `var`("--on-error")
    }
    VoxKBImeUi.VoiceMicButton.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("error"),
    ) {
        background = `var`("--error")
        foreground = `var`("--on-error")
    }

    VoxKBImeUi.VoiceMicButtonIcon.elementName {
        foreground = `var`("--on-primary")
        fontSize = fontSize(38.sp)
    }
    VoxKBImeUi.VoiceMicButtonIcon.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("recording"),
    ) {
        foreground = `var`("--on-error")
    }
    VoxKBImeUi.VoiceMicButtonIcon.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("error"),
    ) {
        foreground = `var`("--on-error")
    }

    VoxKBImeUi.VoiceWaveform.elementName {
        foreground = `var`("--primary")
        fontSize = fontSize(14.sp)
    }
    VoxKBImeUi.VoiceWaveform.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("recording"),
    ) {
        foreground = `var`("--primary")
    }
    VoxKBImeUi.VoiceWaveform.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("clipping"),
    ) {
        foreground = `var`("--warning")
    }

    VoxKBImeUi.VoiceProcessing.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
    }
    VoxKBImeUi.VoiceProcessing.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("error"),
    ) {
        foreground = `var`("--on-error-container")
    }

    VoxKBImeUi.VoiceTranscriptBox.elementName {
        // M3 OutlinedCard-style surface for the transcript.
        background = `var`("--voice-surface-variant")
        foreground = `var`("--on-surface")
        fontSize = fontSize(16.sp)
        padding = padding(16.dp)
        shape = `var`("--shape-variant")
    }

    VoxKBImeUi.VoiceActionBar.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
    }

    VoxKBImeUi.VoiceActionKey.elementName {
        // M3 FilledTonalButton.
        background = `var`("--voice-surface-variant")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        padding = padding(10.dp, 0.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
    }
    VoxKBImeUi.VoiceActionKey.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }
    // Primary action (Insert/Generate) — M3 FilledButton emphasis.
    VoxKBImeUi.VoiceActionKey.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("primary"),
    ) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }

    VoxKBImeUi.VoiceBottomBar.elementName {
        // M3 surfaceContainer-style tonal layer for the footer band.
        background = `var`("--voice-surface")
        foreground = `var`("--on-background")
    }

    VoxKBImeUi.VoiceBottomBarButton.elementName {
        background = `var`("--voice-surface-variant")
        foreground = `var`("--on-surface")
        shape = `var`("--shape-variant")
        fontSize = fontSize(14.sp)
    }
    VoxKBImeUi.VoiceBottomBarButton.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }
    // M3 FilledButton emphasis for the primary switch-to-keyboard action.
    VoxKBImeUi.VoiceBottomBarButton.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("primary"),
    ) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }

    VoxKBImeUi.VoiceEnhanceToggle.elementName {
        background = `var`("--background-variant")
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        padding = padding(4.dp, 12.dp, 4.dp, 12.dp)
        shape = roundedCornerShape(50)
    }
    VoxKBImeUi.VoiceEnhanceToggle.elementName(
        VoxKBImeUi.Attr.VoiceState to listOf("selected"),
    ) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }
}
