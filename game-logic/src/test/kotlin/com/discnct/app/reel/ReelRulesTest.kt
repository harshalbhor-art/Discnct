package com.discnct.app.reel

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ReelRulesTest {

    @Test fun `instagram reel feed is detected by its clips view id`() {
        val ids = setOf(
            "com.instagram.android:id/action_bar",
            "com.instagram.android:id/clips_tab",
        )
        val hit = detectReelSurface("com.instagram.android", ids, browserUrlText = null)
        assertEquals("Instagram Reels", hit?.platform)
        assertEquals("clips_tab", hit?.via)
    }

    @Test fun `instagram main feed without clips views is not a reel`() {
        val ids = setOf(
            "com.instagram.android:id/action_bar",
            "com.instagram.android:id/row_feed_photo_imageview",
        )
        assertNull(detectReelSurface("com.instagram.android", ids, browserUrlText = null))
    }

    @Test fun `youtube shorts is detected by reel_recycler`() {
        val ids = setOf("com.google.android.youtube:id/reel_recycler")
        val hit = detectReelSurface("com.google.android.youtube", ids, browserUrlText = null)
        assertEquals("YouTube Shorts", hit?.platform)
    }

    @Test fun `youtube normal watch page is not flagged`() {
        val ids = setOf("com.google.android.youtube:id/watch_player")
        assertNull(detectReelSurface("com.google.android.youtube", ids, browserUrlText = null))
    }

    @Test fun `tiktok is a whole-app reel regardless of views`() {
        val hit = detectReelSurface("com.zhiliaoapp.musically", emptySet(), browserUrlText = null)
        assertEquals("TikTok", hit?.platform)
        assertEquals("whole-app", hit?.via)
    }

    @Test fun `browser opening youtube shorts url is detected`() {
        val hit = detectReelSurface(
            "com.android.chrome",
            presentNodeIds = emptySet(),
            browserUrlText = "https://m.youtube.com/shorts/abc123",
        )
        assertEquals("Web short-form", hit?.platform)
        assertEquals("youtube.com/shorts", hit?.via)
    }

    @Test fun `browser url matching is case-insensitive`() {
        val hit = detectReelSurface(
            "org.mozilla.firefox",
            presentNodeIds = emptySet(),
            browserUrlText = "HTTPS://WWW.INSTAGRAM.COM/REELS/",
        )
        assertEquals("Web short-form", hit?.platform)
    }

    @Test fun `browser on a normal page is not flagged`() {
        assertNull(
            detectReelSurface(
                "com.android.chrome",
                presentNodeIds = emptySet(),
                browserUrlText = "https://en.wikipedia.org/wiki/Kotlin",
            ),
        )
    }

    @Test fun `blank browser url is ignored`() {
        assertNull(detectReelSurface("com.android.chrome", emptySet(), browserUrlText = "   "))
        assertNull(detectReelSurface("com.android.chrome", emptySet(), browserUrlText = null))
    }

    @Test fun `unknown app is never a reel host`() {
        assertNull(detectReelSurface("com.example.notes", setOf("com.example.notes:id/x"), null))
        assertFalse(isReelHostPackage("com.example.notes"))
        assertNull(reelPlatformFor("com.example.notes"))
    }

    @Test fun `reel hosts include in-app platforms and browsers`() {
        assertTrue(isReelHostPackage("com.instagram.android"))
        assertTrue(isReelHostPackage("com.google.android.youtube"))
        assertTrue(isReelHostPackage("com.android.chrome"))
        assertEquals("Instagram Reels", reelPlatformFor("com.instagram.android")?.displayName)
    }

    @Test fun `node id markers match as substrings of full resource ids`() {
        // A future app build that renames clips_tab -> clips_tab_v2 still matches.
        val ids = setOf("com.instagram.android:id/clips_tab_v2")
        assertTrue(detectReelSurface("com.instagram.android", ids, null) != null)
    }
}
