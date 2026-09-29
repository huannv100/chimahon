package eu.kanade.tachiyomi.ui.dictionary

import android.view.WindowManager.LayoutParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenLookupWindowPolicyTest {
    private val type = LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    @Test
    fun originalSnapshotDoesNotTakeWindowFocus() {
        assertTrue(ScreenLookupWindowPolicy.flagsFor(type) and LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(ScreenLookupWindowPolicy.preservesFocus(type))
    }

    @Test
    fun originalSnapshotStillAcceptsDictionaryTouches() {
        assertEquals(0, ScreenLookupWindowPolicy.flagsFor(type) and LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    @Test
    fun touchesOutsideWindowAreNotModal() {
        assertTrue(ScreenLookupWindowPolicy.flagsFor(type) and LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun ordinaryScreenOcrRetainsExistingKeyboardPolicy() {
        val regular = LayoutParams.TYPE_APPLICATION_OVERLAY
        assertFalse(ScreenLookupWindowPolicy.preservesFocus(regular))
        assertEquals(LayoutParams.FLAG_LAYOUT_NO_LIMITS, ScreenLookupWindowPolicy.flagsFor(regular))
    }
}
