package com.discnct.app.paywall

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PromoCodeTest {

    @Test fun `the exact code is valid`() {
        assertTrue(isValidPromoCode("ryujinfamily"))
    }

    @Test fun `the code is case insensitive`() {
        assertTrue(isValidPromoCode("RyujinFamily"))
        assertTrue(isValidPromoCode("RYUJINFAMILY"))
    }

    @Test fun `surrounding whitespace is ignored`() {
        assertTrue(isValidPromoCode("  ryujinfamily  "))
        assertTrue(isValidPromoCode("\tryujinfamily\n"))
    }

    @Test fun `a wrong code is rejected`() {
        assertFalse(isValidPromoCode("ryujin"))
        assertFalse(isValidPromoCode("ryujinfamilyy"))
        assertFalse(isValidPromoCode(""))
    }

    @Test fun `whitespace in the middle still fails`() {
        assertFalse(isValidPromoCode("ryujin family"))
    }
}
