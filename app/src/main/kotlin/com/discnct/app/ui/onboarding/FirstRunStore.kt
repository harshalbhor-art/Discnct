package com.discnct.app.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.firstRunDataStore by preferencesDataStore(name = "first_run")
private val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")

/**
 * Tracks whether the one-time welcome/how-to-enable popup has been shown. Kept separate from the
 * permission state (PermissionStatus) on purpose: the popup is about *explaining* Android's
 * restricted-settings gate before the user hits it, so it should show exactly once even if the
 * user never finishes granting permissions.
 */
class FirstRunStore(private val context: Context) {

    val welcomeSeen: Flow<Boolean> =
        context.firstRunDataStore.data.map { it[WELCOME_SEEN] ?: false }

    suspend fun setWelcomeSeen() {
        context.firstRunDataStore.edit { it[WELCOME_SEEN] = true }
    }
}
