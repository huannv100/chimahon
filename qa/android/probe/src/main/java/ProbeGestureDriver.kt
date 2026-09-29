package eu.kanade.tachiyomi.ui.dictionary

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.Log

/** Test-only input producer. This capability and receiver do not exist in the production APK. */
internal object ProbeGestureDriver {
    fun install(service: AccessibilityService) {
        service.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val x1 = intent.getFloatExtra("x1", 0f)
                val y1 = intent.getFloatExtra("y1", 0f)
                val x2 = intent.getFloatExtra("x2", x1)
                val y2 = intent.getFloatExtra("y2", y1)
                val duration = intent.getIntExtra("duration", 60).toLong().coerceIn(1, 1000)
                val path = Path().apply {
                    moveTo(x1, y1)
                    if (x1 != x2 || y1 != y2) lineTo(x2, y2)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
                val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.i("ScrollProbe", "INJECTION_COMPLETE")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.i("ScrollProbe", "INJECTION_CANCELLED")
                    }
                }, null)
                Log.i("ScrollProbe", "INJECTION_ACCEPTED $accepted")
            }
        }, IntentFilter("org.chimahon.qa.GESTURE"), Context.RECEIVER_EXPORTED)
    }
}
