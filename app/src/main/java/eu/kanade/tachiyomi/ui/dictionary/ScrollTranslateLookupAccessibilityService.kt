package eu.kanade.tachiyomi.ui.dictionary

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Observes taps handled by Google's Circle to Search / Scroll Translate UI.
 *
 * This service does not intercept touch input. The user's real tap continues to
 * Google, which reveals the original content. We then ask the already-running
 * ScreenLookupService to capture that original frame and open Chimahon OCR.
 */
class ScrollTranslateLookupAccessibilityService : AccessibilityService() {

    private var lastTriggerAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!ScreenLookupServiceState.isRunning.value) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in GOOGLE_SCROLL_TRANSLATE_PACKAGES) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) return
        lastTriggerAt = now

        ScreenLookupService.captureAfterOriginalReveal(this)
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TRIGGER_DEBOUNCE_MS = 500L

        val GOOGLE_SCROLL_TRANSLATE_PACKAGES = setOf(
            "com.google.android.googlequicksearchbox",
        )
    }
}
