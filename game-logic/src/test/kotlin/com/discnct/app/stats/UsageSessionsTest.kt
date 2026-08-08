package com.discnct.app.stats

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

class UsageSessionsTest {

    @Test fun `a session wholly inside the window counts in full`() {
        val session = UsageSession("a", 10L, 60L)
        assertEquals(50L, overlapMillis(session, 0L, 100L))
    }

    @Test fun `a session straddling the start counts only the tail`() {
        val session = UsageSession("a", -30L, 20L)
        assertEquals(20L, overlapMillis(session, 0L, 100L))
    }

    @Test fun `a session straddling the end counts only the head`() {
        val session = UsageSession("a", 80L, 200L)
        assertEquals(20L, overlapMillis(session, 0L, 100L))
    }

    @Test fun `a session entirely outside the window counts for nothing`() {
        assertEquals(0L, overlapMillis(UsageSession("a", 200L, 300L), 0L, 100L))
        assertEquals(0L, overlapMillis(UsageSession("a", -300L, -200L), 0L, 100L))
    }

    @Test fun `a window is half-open so a session ending exactly at the start counts zero`() {
        assertEquals(0L, overlapMillis(UsageSession("a", -50L, 0L), 0L, 100L))
    }

    @Test fun `per-package totals sum repeated sessions`() {
        val sessions = listOf(
            UsageSession("insta", 0L, 30L),
            UsageSession("insta", 40L, 60L),
            UsageSession("mail", 60L, 70L),
        )
        assertEquals(mapOf("insta" to 50L, "mail" to 10L), millisByPackage(sessions, 0L, 100L))
    }

    @Test fun `packages with nothing in the window are dropped entirely`() {
        val sessions = listOf(UsageSession("insta", 0L, 30L), UsageSession("mail", 500L, 600L))
        assertEquals(setOf("insta"), millisByPackage(sessions, 0L, 100L).keys)
    }

    @Test fun `a session spanning midnight splits across both days`() {
        // 23:30 to 00:20 the next day: 30 minutes on day one, 20 on day two.
        val start = DAY - 30 * 60_000L
        val sessions = listOf(UsageSession("insta", start, DAY + 20 * 60_000L))
        val buckets = millisByBucket(sessions, listOf(0L, DAY, 2 * DAY))
        assertEquals(listOf(30 * 60_000L, 20 * 60_000L), buckets)
    }

    @Test fun `empty buckets are still reported so a chart keeps its shape`() {
        val sessions = listOf(UsageSession("insta", 0L, HOUR))
        assertEquals(listOf(HOUR, 0L, 0L), millisByBucket(sessions, listOf(0L, DAY, 2 * DAY, 3 * DAY)))
    }

    @Test fun `time outside the outermost boundaries is dropped`() {
        val sessions = listOf(UsageSession("insta", -DAY, 3 * DAY))
        assertEquals(listOf(DAY, DAY), millisByBucket(sessions, listOf(0L, DAY, 2 * DAY)))
    }

    @Test fun `fewer than two boundaries describe no buckets`() {
        assertEquals(emptyList(), millisByBucket(listOf(UsageSession("a", 0L, 10L)), listOf(0L)))
        assertEquals(emptyList(), millisByBucket(emptyList(), emptyList()))
    }

    @Test fun `bar fractions normalise against the tallest bar`() {
        assertEquals(listOf(0.5f, 1f, 0.25f), barFractions(listOf(50L, 100L, 25L)))
    }

    @Test fun `an all-zero week is a flat chart rather than a divide by zero`() {
        assertEquals(listOf(0f, 0f, 0f), barFractions(listOf(0L, 0L, 0L)))
    }
}
