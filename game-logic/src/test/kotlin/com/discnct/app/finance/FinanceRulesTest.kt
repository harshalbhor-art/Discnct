package com.discnct.app.finance

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FinanceRulesTest {

    @Test fun `a seeded banking app is exempt with no configuration at all`() {
        assertTrue(isFinancialPackage("com.phonepe.app"))
    }

    @Test fun `instagram is not a bank`() {
        assertFalse(isFinancialPackage("com.instagram.android"))
    }

    @Test fun `a bank we have never heard of can be added`() {
        assertTrue(isFinancialPackage("com.example.creditunion", added = setOf("com.example.creditunion")))
    }

    @Test fun `a seeded app the user removed stays removed`() {
        // The seed is a default, not a verdict. Someone who un-exempts an app has decided that
        // Discnct should keep working there, and a later release adding it back would undo that.
        assertFalse(isFinancialPackage("com.phonepe.app", removed = setOf("com.phonepe.app")))
    }

    @Test fun `removing wins over adding the same package`() {
        val exempt = financialPackages(added = setOf("com.example.bank"), removed = setOf("com.example.bank"))
        assertFalse("com.example.bank" in exempt)
    }

    @Test fun `an empty configuration is exactly the seed`() {
        assertEquals(FINANCIAL_PACKAGE_SEED, financialPackages())
    }

    @Test fun `every seeded package looks like a package name`() {
        // A typo here exempts nothing and nobody notices, which is the quietest way this list can
        // be wrong. Shape is all we can check without a device.
        val shape = Regex("""[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+""")
        FINANCIAL_PACKAGE_SEED.forEach { pkg ->
            assertTrue(shape.matches(pkg), "not a package name: $pkg")
        }
    }
}
