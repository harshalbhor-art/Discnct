package com.discnct.app.stats

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class DurationFormatTest {

    @Test fun `minutes only below an hour`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("38m", formatMinutes(38))
        assertEquals("59m", formatMinutes(59))
    }

    @Test fun `a whole hour drops the empty minutes`() {
        assertEquals("1h", formatMinutes(60))
        assertEquals("4h", formatMinutes(240))
    }

    @Test fun `hours and minutes together`() {
        assertEquals("1h 1m", formatMinutes(61))
        assertEquals("4h 12m", formatMinutes(252))
    }

    @Test fun `negative minutes read as zero rather than as a negative duration`() {
        assertEquals("0m", formatMinutes(-5))
    }

    @Test fun `millis round down to the minute`() {
        assertEquals("0m", formatDurationMillis(59_999L))
        assertEquals("1m", formatDurationMillis(60_000L))
        assertEquals("1m", formatDurationMillis(119_999L))
        assertEquals("2h 5m", formatDurationMillis(7_500_000L))
    }

    @Test fun `split keeps the unit out of the display-face number`() {
        assertEquals("38" to "m", splitDuration(38 * 60_000L))
        assertEquals("4" to "h 12m", splitDuration(252 * 60_000L))
        assertEquals("0" to "m", splitDuration(0L))
    }
}
