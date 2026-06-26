/*
 * Copyright (C) 2021-2025 The VoxKB Contributors
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

package com.voxkb.app.apptheme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.voxkb.app.AppTheme
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.lib.android.AndroidVersion
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import com.voxkb.lib.color.neutralDynamicColorScheme
import com.voxkb.lib.color.systemAccentOrDefault


@Composable
fun getColorScheme(
    context: Context,
    theme: AppTheme,
): ColorScheme {
    val prefs by VoxKBPreferenceStore
    val accentColor by prefs.other.accentColor.collectAsState()

    val isDark = when (theme) {
        AppTheme.AUTO, AppTheme.AUTO_AMOLED -> isSystemInDarkTheme()
        AppTheme.DARK, AppTheme.AMOLED_DARK -> true
        AppTheme.LIGHT -> false
    }
    val isAmoled = theme == AppTheme.AUTO_AMOLED || theme == AppTheme.AMOLED_DARK

    // True Material You: on Android 12+, when the user has not picked a custom
    // accent color (isMaterialYou is true for the sentinel Color.Unspecified),
    // derive the whole palette from the system wallpaper instead of regenerating
    // it from a seed. This matches what Gboard and other M3 apps do.
    val useSystemWallpaper = AndroidVersion.ATLEAST_API31_S &&
        accentColor.isMaterialYou(context)

    if (useSystemWallpaper) {
        val systemScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (isAmoled) systemScheme.amoled() else systemScheme
    }

    val seedColor = systemAccentOrDefault(accentColor)
    return neutralDynamicColorScheme(
        primary = seedColor,
        isDark = isDark,
        isAmoled = isAmoled,
    )
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

@Composable
fun VoxKBAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = getColorScheme(context = LocalContext.current, theme = theme)

    val darkTheme =
        theme == AppTheme.DARK
            || theme == AppTheme.AMOLED_DARK
            || (theme == AppTheme.AUTO && isSystemInDarkTheme())
            || (theme == AppTheme.AUTO_AMOLED && isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
