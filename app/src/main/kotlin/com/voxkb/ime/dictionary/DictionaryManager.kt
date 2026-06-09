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

package com.voxkb.ime.dictionary

import android.content.Context
import androidx.room.Room
import com.voxkb.app.VoxKBPreferenceStore
import com.voxkb.ime.nlp.SuggestionCandidate
import com.voxkb.ime.nlp.WordSuggestionCandidate
import com.voxkb.lib.VoxKBLocale
import java.lang.ref.WeakReference

/**
 * TODO: document
 */
class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by VoxKBPreferenceStore

    private var voxkbUserDictionaryDatabase: VoxKBUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

    companion object {
        private var defaultInstance: DictionaryManager? = null

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }

    fun queryUserDictionary(word: String, locale: VoxKBLocale): List<SuggestionCandidate> {
        val voxkbDao = voxkbUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (voxkbDao == null && systemDao == null) {
            return emptyList()
        }
        return buildList {
            if (prefs.dictionary.enableVoxKBUserDictionary.get()) {
                voxkbDao?.query(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
                voxkbDao?.queryShortcut(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
            }
            if (prefs.dictionary.enableSystemUserDictionary.get()) {
                systemDao?.query(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
                systemDao?.queryShortcut(word, locale)?.let {
                    for (entry in it) {
                        add(WordSuggestionCandidate(entry.word, confidence = entry.freq / 255.0))
                    }
                }
            }
        }

    }

    fun spell(word: String, locale: VoxKBLocale): Boolean {
        val voxkbDao = voxkbUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (voxkbDao == null && systemDao == null) {
            return false
        }
        var ret = false
        if (prefs.dictionary.enableVoxKBUserDictionary.get()) {
            ret = ret || voxkbDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || voxkbDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ret = ret || systemDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false
            ret = ret || systemDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false
        }
        return ret
    }

    @Synchronized
    fun voxkbUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableVoxKBUserDictionary.get()) {
            voxkbUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun voxkbUserDictionaryDatabase(): VoxKBUserDictionaryDatabase? {
        return if (prefs.dictionary.enableVoxKBUserDictionary.get()) {
            voxkbUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryDao? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase?.userDictionaryDao()
        } else {
            null
        }
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        val context = applicationContext.get() ?: return

        if (voxkbUserDictionaryDatabase == null && prefs.dictionary.enableVoxKBUserDictionary.get()) {
            voxkbUserDictionaryDatabase = Room.databaseBuilder(
                context,
                VoxKBUserDictionaryDatabase::class.java,
                VoxKBUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }

    @Synchronized
    fun unloadUserDictionariesIfNecessary() {
        if (voxkbUserDictionaryDatabase != null) {
            voxkbUserDictionaryDatabase?.close()
            voxkbUserDictionaryDatabase = null
        }
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
    }
}
