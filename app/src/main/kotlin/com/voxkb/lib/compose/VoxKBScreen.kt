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

package com.voxkb.lib.compose

import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.voxkb.app.VoxKBPreferenceModel
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.app.LocalNavController
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import com.voxkb.lib.android.AndroidVersion
import com.voxkb.lib.compose.VoxKBAppBar
import com.voxkb.lib.compose.VoxKBIconButton
import com.voxkb.lib.compose.autoMirrorForRtl
import com.voxkb.lib.compose.voxkbVerticalScroll

@Composable
fun VoxKBScreen(builder: @Composable VoxKBScreenScope.() -> Unit) {
    val scope = remember { VoxKBScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias VoxKBScreenActions = @Composable RowScope.() -> Unit
typealias VoxKBScreenBottomBar = @Composable () -> Unit
typealias VoxKBScreenContent = PreferenceUiContent<VoxKBPreferenceModel>
typealias VoxKBScreenFab = @Composable () -> Unit
typealias VoxKBScreenNavigationIcon = @Composable () -> Unit

interface VoxKBScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: VoxKBScreenActions)

    fun bottomBar(bottomBar: VoxKBScreenBottomBar)

    fun content(content: VoxKBScreenContent)

    fun floatingActionButton(fab: VoxKBScreenFab)

    fun navigationIcon(navigationIcon: VoxKBScreenNavigationIcon)
}

private class VoxKBScreenScopeImpl : VoxKBScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)

    private var actions: VoxKBScreenActions = @Composable { }
    private var bottomBar: VoxKBScreenBottomBar = @Composable { }
    private var content: VoxKBScreenContent = @Composable { }
    private var fab: VoxKBScreenFab = @Composable { }
    private var navigationIcon: VoxKBScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        VoxKBIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.autoMirrorForRtl(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }

    override fun actions(actions: VoxKBScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: VoxKBScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: VoxKBScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: VoxKBScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: VoxKBScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val previewFieldController = LocalPreviewFieldController.current
        val colorScheme = MaterialTheme.colorScheme

        SideEffect {
            val window = (context as Activity).window
            previewFieldController?.isVisible = previewFieldVisible
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { VoxKBAppBar(title, navigationIcon.takeIf { navigationIconVisible }, actions, scrollBehavior) },
            bottomBar = bottomBar,
            floatingActionButton = fab,
        ) { innerPadding ->
            val scrollModifier = if (scrollable) {
                Modifier.voxkbVerticalScroll()
            } else {
                Modifier
            }
            PreferenceLayout(
                VoxKBPreferenceStore,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .then(scrollModifier),
                iconSpaceReserved = iconSpaceReserved,
                content = content,
            )
        }
    }
}
