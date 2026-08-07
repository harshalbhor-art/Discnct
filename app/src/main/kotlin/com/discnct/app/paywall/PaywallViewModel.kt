package com.discnct.app.paywall

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PaywallUiState(
    // Starts true so nothing flashes a lock screen before the trial-start read comes back — a
    // trial that hasn't started yet reads as active (see isTrialActive), so this is the correct
    // steady state for a brand new install too, not just a loading placeholder.
    val isUnlocked: Boolean = true,
    val trialDaysRemaining: Int = TRIAL_DAYS,
    val priceLabel: String? = null,
    val promoError: Boolean = false,
)

/**
 * Backs the Level 2+ access check and the paywall screen shown once the trial runs out.
 * "Unlocked" is true if any one of three things holds: the trial is still running, a purchase is on
 * record with Play, or the tester promo code was entered — first match wins, and nothing here needs
 * to know which one it was.
 */
class PaywallViewModel(application: Application) : AndroidViewModel(application) {

    private val paywallStore = PaywallStore(application)
    private val billing = BillingRepository(application)

    private val promoError = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        billing.start { viewModelScope.launch { billing.refresh() } }
        viewModelScope.launch {
            combine(
                paywallStore.trialStartMillis,
                paywallStore.promoUnlocked,
                billing.isPurchased,
                billing.priceLabel,
                promoError,
            ) { trialStart, promo, purchased, price, error ->
                val now = System.currentTimeMillis()
                PaywallUiState(
                    isUnlocked = isTrialActive(trialStart, now) || purchased || promo,
                    trialDaysRemaining = trialDaysRemaining(trialStart, now),
                    priceLabel = price,
                    promoError = error,
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Called the first time Level 2 or 3 is opened — starts the trial clock, once, ever. */
    fun startTrialIfNeeded() {
        viewModelScope.launch { paywallStore.startTrialIfNeeded() }
    }

    /** Re-checks Play's purchase record. Call on resume — a purchase can complete outside the app. */
    fun refreshBilling() {
        viewModelScope.launch { billing.refresh() }
    }

    fun buy(activity: Activity) {
        billing.launchPurchase(activity)
    }

    fun submitPromoCode(code: String) {
        if (isValidPromoCode(code)) {
            viewModelScope.launch { paywallStore.setPromoUnlocked() }
        } else {
            promoError.value = true
        }
    }

    fun clearPromoError() {
        promoError.value = false
    }
}
