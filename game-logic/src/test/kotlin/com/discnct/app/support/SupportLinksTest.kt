package com.discnct.app.support

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SupportLinksTest {

    @Test fun `the uri matches the shape a payment app expects`() {
        assertEquals(
            "upi://pay?pa=yourname@upi&pn=Discnct&am=99.00&cu=INR&tn=Discnct%20Support",
            upiPaymentUri(99),
        )
    }

    @Test fun `the amount carries two decimal places`() {
        assertEquals("99.00", upiAmount(99))
        assertEquals("499.00", upiAmount(499))
        assertEquals("1.00", upiAmount(1))
    }

    @Test fun `every preset amount lands in the uri`() {
        // The card offers these four; each has to survive the round trip unchanged.
        for (rupees in listOf(49, 99, 199, 499)) {
            assertTrue(
                upiPaymentUri(rupees).contains("&am=$rupees.00&"),
                "₹$rupees came out as ${upiPaymentUri(rupees)}",
            )
        }
    }

    @Test fun `a free amount is not rounded or clamped`() {
        assertTrue(upiPaymentUri(7).contains("&am=7.00&"))
        assertTrue(upiPaymentUri(250000).contains("&am=250000.00&"))
    }

    @Test fun `a zero or negative amount is refused rather than sent`() {
        // Sending am=0.00 opens the payment app on a payment that cannot complete, which looks like
        // the app is broken rather than like nothing was entered.
        assertFailsWith<IllegalArgumentException> { upiPaymentUri(0) }
        assertFailsWith<IllegalArgumentException> { upiPaymentUri(-50) }
        assertFailsWith<IllegalArgumentException> { upiAmount(0) }
    }

    @Test fun `spaces become percent twenty, never a plus`() {
        // URLEncoder would give "Discnct+Support" here, and the note would reach the payer's
        // statement with a plus sign in it.
        assertTrue(upiPaymentUri(99, note = "Thanks a lot").endsWith("&tn=Thanks%20a%20lot"))
    }

    @Test fun `the at sign in a upi id is left alone`() {
        assertTrue(upiPaymentUri(99, upiId = "someone@okhdfcbank").contains("pa=someone@okhdfcbank&"))
    }

    @Test fun `a payee name cannot break out into another parameter`() {
        // Ampersands and equals signs in a name would otherwise forge extra UPI parameters.
        val uri = upiPaymentUri(99, payeeName = "A&B=C")
        assertTrue(uri.contains("&pn=A%26B%3DC&"), uri)
    }

    @Test fun `non-ascii survives as utf-8`() {
        assertEquals("%E2%82%B9", upiPaymentUri(99, payeeName = "₹").substringAfter("&pn=").substringBefore("&"))
    }

    @Test fun `the currency is always INR`() {
        assertTrue(upiPaymentUri(99).contains("&cu=INR&"))
    }

    @Test fun `the placeholder upi id is still a placeholder`() {
        // A reminder in test form: this fails the day a real ID is set, which is the moment to also
        // check the payee name reads the way it should on someone's bank statement.
        assertEquals("yourname@upi", UPI_ID, "replace the placeholder, then update this test")
    }
}
