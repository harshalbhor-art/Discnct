package com.discnct.app.cover

/**
 * Which picture goes in the middle of a covered feed, and how that choice survives being written
 * to disk.
 *
 * All of this is deliberately Android-free so it can be unit tested. The interesting parts are not
 * the drawing — that's [com.discnct.app.service.FeedOverlayView]'s problem — but the two decisions
 * around it: which image a fresh install starts on, and how a stored choice is parsed back without
 * trusting what's on disk.
 */

/** Where one cover's artwork comes from. */
sealed interface CoverSource {

    /** An image shipped inside the APK, named by its drawable resource. */
    data class Builtin(val drawableName: String) : CoverSource

    /** A picture the user picked, copied into the app's own files directory. */
    data class UserImage(val fileName: String) : CoverSource
}

/** A shipped image, with the name shown under its tile. */
data class BuiltinCoverArt(val drawableName: String, val label: String)

const val COVER_GET_OUT = "get_out"
const val COVER_FIR_KHUL_GAYA = "fir_khul_gaya"

/**
 * Every built-in, in the order the picker shows them.
 *
 * Both ship to everyone. Locale decides only which one a fresh install *starts* on — see
 * [defaultCoverKey] — because the alternative (shipping the meme to Indian builds only) would mean
 * separate build flavours and would stop a Hindi speaker abroad from ever choosing it.
 */
val BUILTIN_COVER_ART: List<BuiltinCoverArt> = listOf(
    BuiltinCoverArt(COVER_FIR_KHUL_GAYA, "Fir khul gaya"),
    BuiltinCoverArt(COVER_GET_OUT, "Get out"),
)

private const val BUILTIN_PREFIX = "builtin:"
private const val FILE_PREFIX = "file:"

/**
 * Languages that mean "this phone probably reads Hinglish".
 *
 * Android hands back either the two- or three-letter code depending on the locale and API level, so
 * both spellings are listed where they differ. Urdu and Nepali are in here knowing they're spoken
 * well beyond India: this only picks a starting image that two taps can change, so a false positive
 * costs nothing and a false negative is the more annoying way to be wrong.
 */
private val INDIAN_LANGUAGE_CODES = setOf(
    "hi", "hin", "bn", "ben", "te", "tel", "mr", "mar", "ta", "tam", "ur", "urd",
    "gu", "guj", "kn", "kan", "ml", "mal", "pa", "pan", "or", "ory", "as", "asm",
    "mai", "sat", "ks", "kas", "ne", "nep", "sd", "snd", "kok", "doi", "mni", "bho",
)

/** True when either the region or the language points at India. */
fun isIndianLocale(country: String?, language: String?): Boolean {
    if (country != null && country.equals("IN", ignoreCase = true)) return true
    return language != null && language.lowercase() in INDIAN_LANGUAGE_CODES
}

/**
 * The image a feed cover starts on when the user has never chosen one.
 *
 * Not stored anywhere at install time — it's computed on read, so a phone that changes locale picks
 * up the other default until the moment the user makes a choice of their own, after which locale
 * stops mattering entirely.
 */
fun defaultCoverKey(country: String?, language: String?): String =
    encodeCoverKey(
        CoverSource.Builtin(
            if (isIndianLocale(country, language)) COVER_FIR_KHUL_GAYA else COVER_GET_OUT,
        ),
    )

/** Flatten a choice into the single string that goes into DataStore. */
fun encodeCoverKey(source: CoverSource): String = when (source) {
    is CoverSource.Builtin -> BUILTIN_PREFIX + source.drawableName
    is CoverSource.UserImage -> FILE_PREFIX + source.fileName
}

/**
 * Read a stored choice back, or null if it's unusable.
 *
 * Null rather than a fallback on purpose: the caller knows the locale and can ask [defaultCoverKey]
 * for the right replacement, which this can't do.
 *
 * User file names are checked rather than trusted. Nothing hostile is expected — the app wrote
 * them — but a stored name is concatenated onto a directory path to open a file, and a name
 * carrying `..` or a separator would read somewhere it shouldn't. It costs one line to make that
 * impossible instead of unlikely.
 */
fun decodeCoverKey(key: String?): CoverSource? = when {
    key == null -> null
    key.startsWith(BUILTIN_PREFIX) ->
        key.removePrefix(BUILTIN_PREFIX)
            .takeIf { name -> BUILTIN_COVER_ART.any { it.drawableName == name } }
            ?.let(CoverSource::Builtin)

    key.startsWith(FILE_PREFIX) ->
        key.removePrefix(FILE_PREFIX)
            .takeIf { isSafeUserFileName(it) }
            ?.let(CoverSource::UserImage)

    else -> null
}

/** A plain file name: no separators, no traversal, nothing empty. */
fun isSafeUserFileName(name: String): Boolean =
    name.isNotBlank() &&
        !name.contains('/') &&
        !name.contains('\\') &&
        name.none { it.isWhitespace() } &&
        name != "." &&
        name != ".."
