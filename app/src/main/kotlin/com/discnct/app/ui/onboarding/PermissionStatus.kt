package com.discnct.app.ui.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.discnct.app.service.DiscnctAccessibilityService

/**
 * These four states can't be requested through a normal runtime-permission dialog —
 * each one is a manual toggle in a system Settings screen, so onboarding can only
 * point the user there and re-check on return (see OnboardingScreen's ON_RESUME check).
 */
object PermissionStatus {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${DiscnctAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun accessibilitySettingsIntent() = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessSettingsIntent() = android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettingsIntent(context: Context) = android.content.Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun batteryOptimizationIntent(context: Context) = android.content.Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
}
