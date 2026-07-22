package com.discnct.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Stub for Phase 0: exists so it can be registered in the manifest and enabled from
 * Settings > Accessibility, which is what the onboarding flow needs to detect. Phase 1
 * fills in [onAccessibilityEvent] with the actual foreground-app blocklist check.
 */
class DiscnctAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
