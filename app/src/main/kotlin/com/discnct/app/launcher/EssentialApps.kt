package com.discnct.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/**
 * The apps a stripped-back home screen has to keep, worked out from what the device says it uses
 * for each job rather than from a hard-coded package list — "the dialler" is a different package
 * on a Pixel, a Samsung and a GrapheneOS build, and guessing wrong here means a home screen with
 * no way to make a phone call.
 *
 * Anything that doesn't resolve is simply skipped: a tablet with no dialler shouldn't get a dead
 * icon, and it shouldn't stop the rest of the list being seeded either.
 */
object EssentialApps {

    fun resolve(context: Context): Set<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Settings.ACTION_SETTINGS),
        )
        val resolved = intents.mapNotNullTo(mutableSetOf()) { intent ->
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeUnless { it == ANDROID_RESOLVER_PACKAGE }
        }
        // Discnct itself, so there's always a route back into settings from its own home screen.
        return resolved + context.packageName
    }

    /** What [PackageManager] reports when several apps match and none is the default. */
    private const val ANDROID_RESOLVER_PACKAGE = "android"
}
