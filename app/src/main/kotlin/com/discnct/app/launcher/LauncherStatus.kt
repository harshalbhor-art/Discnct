package com.discnct.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Whether Discnct is the OS default Home app can't be requested programmatically — Android
 * only lets the user pick a Home app manually in system settings, same constraint as the
 * Level 1 permissions in PermissionStatus. This only reports the current state and opens the
 * relevant settings screen.
 */
object LauncherStatus {

    fun isDefaultHome(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
