package com.discnct.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.discnct.app.ui.applist.BlockListStore
import com.discnct.app.ui.blockscreen.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches for foreground-app changes and, when the app is one of the blocked packages
 * without an active [BlockCooldown], launches [BlockActivity] on top of it. Level 1 only
 * cares about which app is foreground — inspecting screen content for a specific tab
 * (e.g. a Reels feed within Instagram) is Level 2 and needs canRetrieveWindowContent.
 */
class DiscnctAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            BlockListStore(applicationContext).blockedPackages.collect { blockedPackages = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPackage = event.packageName?.toString() ?: return

        if (foregroundPackage == packageName) return
        if (foregroundPackage !in blockedPackages) return
        if (BlockCooldown.isAllowed(foregroundPackage)) return

        startActivity(
            Intent(this, BlockActivity::class.java).apply {
                putExtra(BlockActivity.EXTRA_PACKAGE_NAME, foregroundPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
