package eu.kanade.tachiyomi.ui.dictionary

import android.view.WindowManager

/** Window input policy shared by the original-snapshot overlay and its regression tests. */
internal object ScreenLookupWindowPolicy {
    fun preservesFocus(windowType: Int): Boolean =
        windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun flagsFor(windowType: Int): Int {
        val layout = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (preservesFocus(windowType)) {
            // NOT_FOCUSABLE does not mean NOT_TOUCHABLE: OCR and WebView taps still work.
            // Keep Google/assistant as the focused window; never inject Back to dismiss OCR.
            layout or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            layout
        }
    }
}
