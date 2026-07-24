package com.discnct.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val Context.themeDataStore by preferencesDataStore(name = "theme")
private val THEME_MODE = stringPreferencesKey("theme_mode")

/** Persists the user's light/dark preference, separate from every other settings store. */
class ThemeStore(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[THEME_MODE] = mode.name }
    }
}
