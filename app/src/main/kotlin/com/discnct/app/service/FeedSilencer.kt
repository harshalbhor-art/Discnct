package com.discnct.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Stops the feed making noise while it's covered.
 *
 * A covered feed is still a running feed: a reel keeps playing under the cover, and the controls
 * that would mute it are behind a window that swallows every touch. The user is left with sound
 * they can't reach coming from something they can't see.
 *
 * The fix is audio focus, not volume. [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE] is the
 * system's way of saying "stop, don't duck, I'll hand it back" — it's what a phone call uses.
 * Instagram honours it. Nothing is muted, no setting of the user's is touched, and whatever they
 * were listening to before picks up again on its own when [release] runs, because that is what
 * *transient* means.
 *
 * The alternative — muting `STREAM_MUSIC` outright — would silence music the user deliberately
 * started, and would leave the phone silent if this service died while holding the mute.
 *
 * Main thread only, to match the rest of the cover.
 */
class FeedSilencer(context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Held while we have focus, and the same instance has to be passed back to abandon it. */
    private var held: AudioFocusRequest? = null

    /**
     * We don't act on focus changes; losing it means the user started something themselves, which
     * is theirs to decide. The listener is here because a request without one can't be abandoned
     * cleanly.
     */
    private val listener = AudioManager.OnAudioFocusChangeListener { }

    /** Ask whatever is playing to stop. Safe to call when we already hold focus. */
    fun silence() {
        if (held != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener(listener)
            .setWillPauseWhenDucked(true)
            .build()
        val granted = runCatching { audioManager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) held = request
    }

    /** Hand it back. Safe to call when we never had it. */
    fun release() {
        held?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
        held = null
    }
}
