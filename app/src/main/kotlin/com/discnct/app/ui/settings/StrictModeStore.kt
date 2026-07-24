package com.discnct.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.strictModeDataStore by preferencesDataStore(name = "strict_mode")
private val STRICT_ENABLED = booleanPreferencesKey("strict_enabled")
private val PIN_HASH = stringPreferencesKey("pin_hash")

/**
 * "Strict Mode" — the accountability feature from paid blockers: once on, weakening
 * protection (turning off the whole-app blocker for an app, the reel blocker, or pausing
 * everything) requires the PIN you set here. The PIN is hashed at rest; this is meant as
 * friction against an impulsive tap, not as real security, so a plain unsalted SHA-256 is
 * enough — nothing sensitive is being protected.
 */
class StrictModeStore(private val context: Context) {
    val enabled: Flow<Boolean> = context.strictModeDataStore.data.map { it[STRICT_ENABLED] ?: false }
    val hasPin: Flow<Boolean> = context.strictModeDataStore.data.map { !it[PIN_HASH].isNullOrEmpty() }

    suspend fun setEnabled(enabled: Boolean) {
        context.strictModeDataStore.edit { it[STRICT_ENABLED] = enabled }
    }

    suspend fun setPin(pin: String) {
        context.strictModeDataStore.edit { it[PIN_HASH] = hash(pin) }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val storedHash = context.strictModeDataStore.data.map { it[PIN_HASH] }.first()
        return storedHash != null && storedHash == hash(pin)
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
