package eu.kanade.tachiyomi.ui.dictionary

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

/**
 * Original-window OCR from a floating button. No global touch listener, touch exploration,
 * injected gesture, Google dismissal action, or MediaProjection session.
 */
class ScrollTranslateLookupAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val session = ScrollLookupSession()
    private var overlay: ScreenLookupOverlayController? = null
    private var operational = false
    private var refreshScheduled = false
    private var targets = emptyList<CaptureTarget>()
    private var blockedUntil = 0L
    private var lastDisplayBounds: Rect? = null
    private var floatingButton: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null
    private val windowOwners = linkedMapOf<Int, String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            message("Original-window OCR requires Android 14 or later.")
            disableSelf()
            return
        }
        // Clear flags from an older installed test build; never request these modes.
        serviceInfo?.let { info ->
            info.flags = info.flags and
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
            serviceInfo = info
        }
        operational = true
        activeInstance = this
        // Do not leave the legacy screen-recording OCR session running alongside this one.
        if (ScreenLookupServiceState.isRunning.value) ScreenLookupService.stop(this)
        showFloatingButton()
        message("Floating OCR ready. Use the OCR button; screen taps and scrolling are unchanged.")
        scheduleRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val owner = event.packageName?.toString()
        if (owner != null && event.windowId >= 0) {
            windowOwners[event.windowId] = owner
            if (windowOwners.size > 64) windowOwners.remove(windowOwners.keys.first())
        }
        if (operational) scheduleRefresh()
    }

    override fun onInterrupt() {
        // This interrupts accessibility feedback, not the service lifecycle. There is no
        // speech/haptic feedback to stop. Do not destroy the OCR or its floating button.
        Log.d(TAG, "Feedback interrupted; retaining floating OCR session")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        if (!operational || refreshScheduled) return
        refreshScheduled = true
        handler.postDelayed({
            refreshScheduled = false
            if (operational) refreshWindows()
        }, 80)
    }

    private fun refreshWindows() {
        runCatching {
            val displayBounds = requiredService<WindowManager>().currentWindowMetrics.bounds
            val previousBounds = lastDisplayBounds
            lastDisplayBounds = Rect(displayBounds)
            // A theme/configuration event alone must not close OCR. Only geometry changes
            // invalidate the captured text coordinates.
            if (previousBounds != null && previousBounds != displayBounds) {
                session.reset()
                overlay?.dismiss()
                clampFloatingButton()
            }
            targets = windows.filter { it.displayId == Display.DEFAULT_DISPLAY }
                .sortedByDescending { it.layer }.mapNotNull { window ->
                    val owner = window.root?.packageName?.toString() ?: windowOwners[window.id]
                        ?: return@mapNotNull null
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION ||
                        owner == packageName || owner in EXCLUDED_PACKAGES
                    ) return@mapNotNull null
                    val bounds = Rect().also { window.getBoundsInScreen(it) }
                    if (bounds.isEmpty || !Rect.intersects(bounds, displayBounds)) return@mapNotNull null
                    CaptureTarget(window.id, Rect(bounds), Rect(displayBounds))
                }
            if (locked()) {
                session.reset()
                overlay?.dismiss()
                floatingButton?.visibility = View.INVISIBLE
            } else if (overlay?.isShowing != true) {
                floatingButton?.visibility = View.VISIBLE
            }
        }.onFailure {
            targets = emptyList()
            Log.w(TAG, "Unable to inspect app windows", it)
        }
    }

    private fun captureOriginal(target: CaptureTarget) {
        if (!operational || locked()) return
        val ticket = session.begin() ?: return
        handler.postDelayed({
            if (session.isPending(ticket)) captureFailed(ticket, "Screen capture timed out; try again.")
        }, 2000)
        try {
            takeScreenshotOfWindow(target.id, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        if (!operational || !session.isPending(ticket)) return
                        refreshWindows()
                        if (!session.isPending(ticket)) return
                        if (locked() || targets.none { it == target }) {
                            captureFailed(ticket, "The app or orientation changed; tap the OCR button again.")
                            return
                        }
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?: error("No screenshot pixels")
                        val original = try {
                            hardware.copy(Bitmap.Config.ARGB_8888, false) ?: error("Cannot copy screenshot")
                        } finally {
                            hardware.recycle()
                        }
                        val frame = try {
                            Bitmap.createBitmap(target.display.width(), target.display.height(), Bitmap.Config.ARGB_8888).also {
                                val destination = Rect(target.bounds).apply { offset(-target.display.left, -target.display.top) }
                                Canvas(it).apply {
                                    drawColor(Color.BLACK)
                                    drawBitmap(original, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
                                }
                            }
                        } finally {
                            original.recycle()
                        }
                        if (!session.complete(ticket)) {
                            frame.recycle()
                            return
                        }
                        val controller = overlay ?: ScreenLookupOverlayController(
                            context = this@ScrollTranslateLookupAccessibilityService,
                            windowManager = requiredService<WindowManager>(),
                            onDismiss = {
                                session.reset()
                                blockedUntil = SystemClock.uptimeMillis() + 250
                                floatingButton?.visibility = if (locked()) View.INVISIBLE else View.VISIBLE
                                handler.postDelayed({ scheduleRefresh() }, 260)
                            },
                            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            showOriginalSnapshot = true,
                            dismissOnEmptyTap = true,
                        ).also { overlay = it }
                        floatingButton?.visibility = View.INVISIBLE
                        controller.show(frame)
                    } catch (error: Exception) {
                        Log.w(TAG, "Original capture/overlay failed", error)
                        overlay?.dismiss()
                        session.reset()
                        floatingButton?.visibility = if (locked()) View.INVISIBLE else View.VISIBLE
                        message("Could not open original OCR. Protected screens cannot be captured.")
                        scheduleRefresh()
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    captureFailed(ticket, "Original capture failed (Android error $errorCode). No translated-screen fallback was used.")
                }
            })
        } catch (error: Exception) {
            Log.w(TAG, "Window capture request failed", error)
            captureFailed(ticket, "Android could not capture the original app window.")
        }
    }

    private fun captureFailed(ticket: Long, text: String) {
        if (!session.isPending(ticket)) return
        session.reset()
        blockedUntil = SystemClock.uptimeMillis() + 600
        floatingButton?.visibility = if (locked()) View.INVISIBLE else View.VISIBLE
        message(text)
        handler.postDelayed({ scheduleRefresh() }, 610)
    }

    private fun locked(): Boolean =
        requiredService<KeyguardManager>().isKeyguardLocked ||
            !requiredService<PowerManager>().isInteractive

    private inline fun <reified T : Any> requiredService(): T =
        requireNotNull(getSystemService(T::class.java)) { "Missing Android service: ${T::class.java.simpleName}" }

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun showFloatingButton() {
        if (!operational || floatingButton != null) return
        val wm = requiredService<WindowManager>()
        val size = buttonSizeDp().coerceIn(32, 112).dp
        val backgroundColor = buttonBackgroundColor()
        val button = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backgroundColor)
            }
            elevation = 8.dp.toFloat()
            alpha = buttonAlpha().coerceIn(0.25f, 1f)
            contentDescription = "Screen OCR"
            setOnClickListener { triggerManualLookup() }
            addView(
                ImageView(this@ScrollTranslateLookupAccessibilityService).apply {
                    setImageResource(R.drawable.ic_chimahon)
                    imageTintList = ColorStateList.valueOf(buttonIconColor(backgroundColor))
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(10.dp, 10.dp, 10.dp, 10.dp)
                },
                FrameLayout.LayoutParams(size, size),
            )
        }
        val bounds = wm.currentWindowMetrics.bounds
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "Chimahon floating original OCR"
            gravity = Gravity.TOP or Gravity.START
            x = (bounds.width() - size - 16.dp).coerceAtLeast(0)
            y = (bounds.height() * 0.42f).roundToInt()
        }
        button.installDragHandler(params)
        runCatching {
            wm.addView(button, params)
            floatingButton = button
            floatingButtonParams = params
        }.onFailure {
            Log.w(TAG, "Cannot show floating OCR button", it)
            message("Could not show the OCR button. Disable and re-enable the accessibility service.")
        }
    }

    private fun View.installDragHandler(params: WindowManager.LayoutParams) {
        val wm = requiredService<WindowManager>()
        val touchSlop = ViewConfiguration.get(this@ScrollTranslateLookupAccessibilityService).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var canceled = false
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    canceled = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> canceled = true
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).roundToInt()
                    val dy = (event.rawY - downRawY).roundToInt()
                    moved = moved || kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop
                    if (moved && !canceled) {
                        val bounds = wm.currentWindowMetrics.bounds
                        params.x = (startX + dx).coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
                        params.y = (startY + dy).coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(this, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved && !canceled) performClick()
                MotionEvent.ACTION_CANCEL -> canceled = true
            }
            true
        }
    }

    private fun clampFloatingButton() {
        val button = floatingButton ?: return
        val params = floatingButtonParams ?: return
        val wm = requiredService<WindowManager>()
        val bounds = wm.currentWindowMetrics.bounds
        params.x = params.x.coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(button, params) }
    }

    private fun removeFloatingButton() {
        val wm = getSystemService(WindowManager::class.java)
        floatingButton?.let { view -> runCatching { wm?.removeView(view) } }
        floatingButton = null
        floatingButtonParams = null
    }

    private fun buttonSizeDp(): Int = runCatching {
        Injekt.get<DictionaryPreferences>().ocrButtonSize().get()
    }.getOrDefault(56)

    private fun buttonAlpha(): Float = runCatching {
        Injekt.get<DictionaryPreferences>().ocrButtonAlpha().get()
    }.getOrDefault(0.92f)

    private fun buttonBackgroundColor(): Int {
        val stored = runCatching {
            Injekt.get<DictionaryPreferences>().ocrButtonColor().get()
        }.getOrDefault(0)
        return if (stored != 0) stored else ContextCompat.getColor(this, R.color.tachiyomi_primary)
    }

    private fun buttonIconColor(background: Int): Int {
        val luminance = 0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)
        return if (luminance < 128) Color.WHITE else Color.BLACK
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private fun triggerManualLookup() {
        if (!operational || locked() || SystemClock.uptimeMillis() < blockedUntil) return
        if (overlay?.isShowing == true) {
            overlay?.dismiss()
            return
        }
        if (session.state != ScrollLookupSession.State.IDLE) return
        refreshWindows()
        val target = targets.firstOrNull() ?: run {
            message("No capturable app window found. Return to the content and try again.")
            return
        }
        captureOriginal(target)
    }

    private fun shutdown() {
        if (activeInstance === this) activeInstance = null
        operational = false
        session.reset()
        handler.removeCallbacksAndMessages(null)
        refreshScheduled = false
        overlay?.release()
        overlay = null
        removeFloatingButton()
        windowOwners.clear()
        targets = emptyList()
    }

    private data class CaptureTarget(val id: Int, val bounds: Rect, val display: Rect)

    companion object {
        private const val TAG = "ChimahonOriginalOcr"
        @Volatile private var activeInstance: ScrollTranslateLookupAccessibilityService? = null

        /** Reuse this button instead of opening a second screen-recording permission flow. */
        fun showButtonIfConnected(): Boolean {
            val service = activeInstance?.takeIf { it.operational } ?: return false
            service.handler.post {
                if (service.operational) {
                    service.showFloatingButton()
                    service.clampFloatingButton()
                    service.scheduleRefresh()
                }
            }
            return true
        }

        private val EXCLUDED_PACKAGES = setOf(
            "com.google.android.googlequicksearchbox",
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
        )
    }
}
