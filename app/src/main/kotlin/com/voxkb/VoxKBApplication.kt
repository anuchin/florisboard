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

package com.voxkb

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.voxkb.app.VoxKBPreferenceModel
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.ime.clipboard.ClipboardManager
import com.voxkb.ime.core.SubtypeManager
import com.voxkb.ime.dictionary.DictionaryManager
import com.voxkb.ime.editor.EditorInstance
import com.voxkb.ime.keyboard.KeyboardManager
import com.voxkb.ime.media.emoji.VoxKBEmojiCompat
import com.voxkb.ime.nlp.NlpManager
import com.voxkb.ime.text.gestures.GlideTypingManager
import com.voxkb.ime.theme.ThemeManager
import com.voxkb.lib.cache.CacheManager
import com.voxkb.lib.crashutility.CrashUtility
import com.voxkb.lib.devtools.Flog
import com.voxkb.lib.devtools.LogTopic
import com.voxkb.lib.devtools.flogError
import com.voxkb.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.voxkb.lib.kotlin.io.deleteContentsRecursively
import com.voxkb.lib.kotlin.tryOrNull
import com.voxkb.libnative.dummyAdd
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [VoxKBApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var VoxKBApplicationReference = WeakReference<VoxKBApplication?>(null)

@Suppress("unused")
class VoxKBApplication : Application() {
    companion object {
        init {
            try {
                System.loadLibrary("fl_native")
            } catch (_: Exception) {
            }
        }
    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        VoxKBApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            VoxKBEmojiCompat.init(this)
            flogError { "dummy result: ${dummyAdd(3,4)}" }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = VoxKBPreferenceStore.initAndroid(
                context = this@VoxKBApplication,
                datastoreName = VoxKBPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.voxkbApplication(): VoxKBApplication {
    return when (this) {
        is VoxKBApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.voxkbApplication()
            else -> VoxKBApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as VoxKBApplication } ?: VoxKBApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.voxkbApplication())

fun Context.cacheManager() = this.voxkbApplication().cacheManager

fun Context.clipboardManager() = this.voxkbApplication().clipboardManager

fun Context.editorInstance() = this.voxkbApplication().editorInstance

fun Context.extensionManager() = this.voxkbApplication().extensionManager

fun Context.glideTypingManager() = this.voxkbApplication().glideTypingManager

fun Context.keyboardManager() = this.voxkbApplication().keyboardManager

fun Context.nlpManager() = this.voxkbApplication().nlpManager

fun Context.subtypeManager() = this.voxkbApplication().subtypeManager

fun Context.themeManager() = this.voxkbApplication().themeManager
