package com.discnct.app.paywall

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.paywallDataStore by preferencesDataStore(name = "paywall")
private val TRIAL_START = longPreferencesKey("trial_start_millis")
private val PROMO_UNLOCKED = booleanPreferencesKey("promo_unlocked")

/**
 * Local state for the Level 2+ paywall: when the trial clock started, and whether a promo code
 * unlocked it. A real purchase is *not* tracked here — Play's own purchase record is the source of
 * truth for that, re-checked from [BillingRepository] rather than cached, so a refund or a reinstall
 * on the same account both resolve themselves for free with nothing to keep in sync.
 */
class PaywallStore(private val context: Context) {

    val trialStartMillis: Flow<Long?> =
        context.paywallDataStore.data.map { it[TRIAL_START] }

    val promoUnlocked: Flow<Boolean> =
        context.paywallDataStore.data.map { it[PROMO_UNLOCKED] ?: false }

    /** No-op once already set — the trial starts once, at the first Level 2+ open, ever. */
    suspend fun startTrialIfNeeded() {
        context.paywallDataStore.edit {
            if (it[TRIAL_START] == null) it[TRIAL_START] = System.currentTimeMillis()
        }
    }

    suspend fun setPromoUnlocked() {
        context.paywallDataStore.edit { it[PROMO_UNLOCKED] = true }
    }
}
