package com.discnct.app.cover

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverArtLogicTest {

    @Test
    fun `region IN gets the Hinglish default whatever the language`() {
        assertEquals(
            encodeCoverKey(CoverSource.Builtin(COVER_FIR_KHUL_GAYA)),
            defaultCoverKey(country = "IN", language = "en"),
        )
    }

    @Test
    fun `an Indian language abroad still gets the Hinglish default`() {
        // The case worth having: a Hindi speaker on a US phone reads the joke fine.
        assertEquals(
            encodeCoverKey(CoverSource.Builtin(COVER_FIR_KHUL_GAYA)),
            defaultCoverKey(country = "AE", language = "hi"),
        )
    }

    @Test
    fun `everyone else starts on GET OUT`() {
        assertEquals(
            encodeCoverKey(CoverSource.Builtin(COVER_GET_OUT)),
            defaultCoverKey(country = "US", language = "en"),
        )
    }

    @Test
    fun `an unknown locale falls back rather than failing`() {
        assertEquals(
            encodeCoverKey(CoverSource.Builtin(COVER_GET_OUT)),
            defaultCoverKey(country = null, language = null),
        )
    }

    @Test
    fun `country matching ignores case`() {
        assertTrue(isIndianLocale(country = "in", language = null))
        assertTrue(isIndianLocale(country = "In", language = null))
    }

    @Test
    fun `three letter language codes are recognised too`() {
        // Android hands back either spelling depending on locale and API level.
        assertTrue(isIndianLocale(country = "US", language = "hin"))
        assertTrue(isIndianLocale(country = "US", language = "ory"))
    }

    @Test
    fun `a language that merely contains an Indian code is not a match`() {
        assertFalse(isIndianLocale(country = "US", language = "hindiish"))
        assertFalse(isIndianLocale(country = "GB", language = "en"))
    }

    @Test
    fun `built-in keys round trip`() {
        for (art in BUILTIN_COVER_ART) {
            val source = CoverSource.Builtin(art.drawableName)
            assertEquals(source, decodeCoverKey(encodeCoverKey(source)))
        }
    }

    @Test
    fun `user image keys round trip`() {
        val source = CoverSource.UserImage("cover_1712345678901.webp")
        assertEquals(source, decodeCoverKey(encodeCoverKey(source)))
    }

    @Test
    fun `a built-in that no longer ships decodes to nothing`() {
        // What a downgrade looks like: a key written by a build that had an image this one doesn't.
        assertNull(decodeCoverKey("builtin:some_image_we_removed"))
    }

    @Test
    fun `junk decodes to nothing rather than guessing`() {
        assertNull(decodeCoverKey(null))
        assertNull(decodeCoverKey(""))
        assertNull(decodeCoverKey("get_out"))
        assertNull(decodeCoverKey("builtin:"))
        assertNull(decodeCoverKey("file:"))
    }

    @Test
    fun `a file name that could escape the directory is rejected`() {
        assertNull(decodeCoverKey("file:../../databases/settings.db"))
        assertNull(decodeCoverKey("file:sub/dir.webp"))
        assertNull(decodeCoverKey("file:..\\windows"))
        assertNull(decodeCoverKey("file:.."))
        assertNull(decodeCoverKey("file:."))
    }

    @Test
    fun `safe file names are the ordinary ones`() {
        assertTrue(isSafeUserFileName("cover_1.webp"))
        assertFalse(isSafeUserFileName(""))
        assertFalse(isSafeUserFileName("   "))
        assertFalse(isSafeUserFileName("has space.webp"))
    }

    @Test
    fun `both built-ins ship, in picker order`() {
        assertEquals(
            listOf(COVER_FIR_KHUL_GAYA, COVER_GET_OUT),
            BUILTIN_COVER_ART.map { it.drawableName },
        )
    }
}
