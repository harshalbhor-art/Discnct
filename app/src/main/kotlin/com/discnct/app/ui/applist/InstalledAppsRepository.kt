package com.discnct.app.ui.applist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

/** Reads the device's launchable apps. Requires QUERY_ALL_PACKAGES (see AndroidManifest). */
class InstalledAppsRepository(private val context: Context) {

    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filterNot { it.packageName == context.packageName }
            .filterNot { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && !isUpdatedSystemApp(it) }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun isUpdatedSystemApp(info: ApplicationInfo) =
        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
