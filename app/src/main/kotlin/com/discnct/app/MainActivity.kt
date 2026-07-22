package com.discnct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.discnct.app.ui.applist.AppListScreen
import com.discnct.app.ui.onboarding.OnboardingScreen
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.theme.DiscnctTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiscnctTheme {
                var onboardingDone by remember { mutableStateOf(allPermissionsGranted()) }

                if (onboardingDone) {
                    AppListScreen()
                } else {
                    OnboardingScreen(onComplete = { onboardingDone = true })
                }
            }
        }
    }

    private fun allPermissionsGranted(): Boolean =
        PermissionStatus.isAccessibilityServiceEnabled(this) &&
            PermissionStatus.isUsageAccessGranted(this) &&
            PermissionStatus.isOverlayGranted(this) &&
            PermissionStatus.isIgnoringBatteryOptimizations(this)
}
