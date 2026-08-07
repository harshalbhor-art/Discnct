package com.discnct.app.paywall

/**
 * The one code that bypasses the Level 2+ paywall — handed out by hand to people testing the app,
 * never sold. Release builds here aren't obfuscated (`isMinifyEnabled = false`), so this is a soft
 * gate for testers, not a real secret; treat it the same as a debug backdoor, not as payment fraud
 * prevention.
 */
private const val TEST_PROMO_CODE = "ryujinfamily"

/** Case- and whitespace-insensitive: a code typed on a phone keyboard should still match. */
fun isValidPromoCode(entered: String): Boolean =
    entered.trim().equals(TEST_PROMO_CODE, ignoreCase = true)
