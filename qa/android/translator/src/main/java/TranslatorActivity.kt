package org.chimahon.qa.translator

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class TranslatorActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val wm = requireNotNull(applicationContext.getSystemService(WindowManager::class.java))
        val bounds = wm.currentWindowMetrics.bounds
        val overlay = TextView(applicationContext).apply {
            text = "Synthetic translated text"
            contentDescription = "Synthetic translated surface"
            textSize = 22f
            setTextColor(Color.BLACK)
            setBackgroundColor(0xff00cc33.toInt())
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) Log.i("ScrollProbe", "TRANSLATION_CLICK")
                true
            }
        }
        wm.addView(overlay, WindowManager.LayoutParams(
            bounds.width() * 3 / 5, bounds.height() / 2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = bounds.height() / 4
            title = "Synthetic translation"
        })
        Log.i("ScrollProbe", "TRANSLATION_READY")
        moveTaskToBack(true)
    }
}
