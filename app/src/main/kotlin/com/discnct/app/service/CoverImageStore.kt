package com.discnct.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.discnct.app.cover.CoverSource
import com.discnct.app.cover.decodeCoverKey
import com.discnct.app.cover.defaultCoverKey
import com.discnct.app.cover.encodeCoverKey
import com.discnct.app.cover.isSafeUserFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val Context.coverImageDataStore by preferencesDataStore(name = "cover_images")

/** Where imported pictures live, under the app's private files directory. */
const val COVER_ART_DIR = "cover_art"

/**
 * Which picture each app's feed cover uses.
 *
 * Per-app rather than global because that's how it was asked for, and because the covers are
 * per-app already — one image for Instagram and another for Facebook costs nothing here and reads
 * as obviously right on the screen.
 *
 * A package with no entry is *not* written a default at install time. It resolves through
 * [com.discnct.app.cover.defaultCoverKey] on read instead, so the locale rule keeps applying until
 * the moment the user picks something, and from then on their choice is the only thing consulted.
 */
class CoverImageStore(private val context: Context) {

    /** Every stored choice, keyed by package name. Packages the user hasn't touched are absent. */
    val coverKeys: Flow<Map<String, String>> = context.coverImageDataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { entry ->
            (entry.value as? String)?.let { stored -> entry.key.name to stored }
        }.toMap()
    }

    /** The choice for one app, already resolved against the locale default. */
    fun coverSource(packageName: String): Flow<CoverSource?> = coverKeys.map { keys ->
        resolve(keys[packageName])
    }

    suspend fun setCover(packageName: String, source: CoverSource) {
        context.coverImageDataStore.edit { it[stringPreferencesKey(packageName)] = encodeCoverKey(source) }
    }

    /**
     * Fall back to the locale default when there's no stored choice — or when the stored one no
     * longer decodes, which is what a deleted picture or a downgraded build looks like from here.
     */
    fun resolve(key: String?): CoverSource? {
        decodeCoverKey(key)?.let { return it }
        val locale = deviceLocale()
        return decodeCoverKey(defaultCoverKey(locale?.country, locale?.language))
    }

    /**
     * The phone's locale, or null if it somehow reports none.
     *
     * Read from the configuration rather than [Locale.getDefault] so a per-app language override —
     * Android 13's "app languages" screen — is what counts, since that's the language this user
     * chose to read *Discnct* in.
     */
    private fun deviceLocale(): Locale? {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) null else locales[0]
    }

    private val imageDir: File get() = File(context.filesDir, COVER_ART_DIR)

    /** The file a user image lives in. Nothing guarantees it exists. */
    fun fileFor(image: CoverSource.UserImage): File = File(imageDir, image.fileName)

    /** Every picture the user has imported, newest first. */
    fun userImages(): List<CoverSource.UserImage> =
        imageDir.listFiles()
            ?.filter { it.isFile && isSafeUserFileName(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { CoverSource.UserImage(it.name) }
            .orEmpty()

    /**
     * Copy a picked picture into the app's own storage, square and no larger than the card that
     * will draw it.
     *
     * Three reasons this re-encodes rather than copying the bytes across. The picker hands back a
     * borrowed URI whose permission does not survive a reboot, so the file has to be ours. A phone
     * camera photo is many megapixels and would be decoded, in full, by an accessibility service
     * that only needs a square a fraction of that size. And the card is square, so a portrait photo
     * has to be cropped by *something* — doing it here once beats doing it on every draw.
     *
     * Returns null if the picture can't be read or decoded, which callers surface rather than
     * pretending an image was added.
     */
    suspend fun importImage(uri: Uri): CoverSource.UserImage? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSizeFor(minOf(bounds.outWidth, bounds.outHeight), TARGET_PX)
                    },
                )
            } ?: return@runCatching null

            val square = centreCropSquare(decoded)
            imageDir.mkdirs()
            val file = File(imageDir, "cover_${System.currentTimeMillis()}.webp")
            file.outputStream().use { out -> square.compress(webpFormat(), QUALITY, out) }
            if (square !== decoded) decoded.recycle()
            square.recycle()
            CoverSource.UserImage(file.name)
        }.getOrNull()
    }

    /** Remove an imported picture. Any app still pointing at it falls back on next read. */
    suspend fun deleteUserImage(image: CoverSource.UserImage) = withContext(Dispatchers.IO) {
        runCatching { fileFor(image).delete() }
        Unit
    }

    private fun centreCropSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = if (source.width == source.height) {
            source
        } else {
            Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        }
        if (side <= TARGET_PX) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_PX, TARGET_PX, true)
        if (cropped !== source) cropped.recycle()
        return scaled
    }

    /** The largest power-of-two downscale that still leaves the image wider than [target]. */
    private fun sampleSizeFor(source: Int, target: Int): Int {
        if (source <= 0 || target <= 0) return 1
        var sample = 1
        while (source / (sample * 2) >= target) sample *= 2
        return sample
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            // minSdk is 26, and the split lossy/lossless formats only arrived in R.
            Bitmap.CompressFormat.WEBP
        }

    private companion object {
        /** Matches the built-in artwork, which is what the card is sized around. */
        const val TARGET_PX = 1254
        const val QUALITY = 85
    }
}
