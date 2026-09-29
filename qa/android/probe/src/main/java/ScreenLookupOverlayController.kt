package eu.kanade.tachiyomi.ui.dictionary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/** Test viewer only: the real Chimahon OCR/popup is verified by its app build, not this probe. */
internal class ScreenLookupOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDismiss: () -> Unit,
    private val windowType: Int,
    private val showOriginalSnapshot: Boolean,
    private val dismissOnEmptyTap: Boolean,
) {
    private var view: View? = null
    fun show(bitmap: Bitmap) {
        val image = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = "Original probe snapshot"
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) dismiss()
                true
            }
        }
        val params = WindowManager.LayoutParams(
            -1, -1, windowType, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
            title = "Chimahon original probe"
        }
        windowManager.addView(image, params)
        view = image
        Log.i("ScrollProbe", "OPEN pixel=${Integer.toHexString(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))}")
    }
    fun dismiss() {
        val current = view ?: return
        view = null
        windowManager.removeView(current)
        Log.i("ScrollProbe", "CLOSE")
        onDismiss()
    }
    fun release() = dismiss()
}
