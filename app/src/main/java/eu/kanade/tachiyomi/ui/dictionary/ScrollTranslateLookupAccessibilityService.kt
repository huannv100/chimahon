package eu.kanade.tachiyomi.ui.dictionary

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.TouchInteractionController
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Opt in by enabling this service in Android Accessibility settings (Android 14+).
 * No Google click events, simulated taps, Back actions, or MediaProjection session.
 * Short taps are consumed; Android delegates swipes. Lookup is a modal snapshot.
 */
class ScrollTranslateLookupAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val session = ScrollLookupSession()
    private var touchController: TouchInteractionController? = null
    private var policy: ScrollLookupTapPolicy? = null
    private var overlay: ScreenLookupOverlayController? = null
    private var operational = false
    private var routing = false
    private var refreshScheduled = false
    private var delegateRequested = false
    private var currentTarget: CaptureTarget? = null
    private var pendingTarget: CaptureTarget? = null
    private var targets = emptyList<CaptureTarget>()
    private var googleControls = emptyList<Rect>()
    private var protectedWindows = emptyList<Rect>()
    private var googleVisible = false
    private var keyboardVisible = false
    private var blockedUntil = 0L
    private var warnedConflict = false
    private val windowOwners = mutableMapOf<Int, String>()

    private val holdTimeout = Runnable {
        if (policy?.timeout() == ScrollLookupTapPolicy.Decision.DELEGATE) delegateGesture()
    }

    private val touchCallback = object : TouchInteractionController.Callback {
        override fun onMotionEvent(event: MotionEvent) {
            if (!operational) {
                delegateGesture()
                return
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    delegateRequested = false
                    currentTarget = eligibleTarget(event.x, event.y)
                    val decision = policy?.down(event.x, event.y, event.eventTime, currentTarget != null)
                    if (decision == ScrollLookupTapPolicy.Decision.DELEGATE) {
                        delegateGesture()
                    } else {
                        handler.postDelayed(holdTimeout, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Include batched history: a swipe returning to its start is not a tap.
                    for (i in 0 until event.historySize) {
                        if (policy?.move(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i)) ==
                            ScrollLookupTapPolicy.Decision.DELEGATE
                        ) delegateGesture()
                    }
                    if (policy?.move(event.x, event.y, event.eventTime, event.pointerCount) ==
                        ScrollLookupTapPolicy.Decision.DELEGATE
                    ) delegateGesture()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    policy?.cancel()
                    delegateGesture()
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(holdTimeout)
                    when (policy?.up(event.x, event.y, event.eventTime)) {
                        ScrollLookupTapPolicy.Decision.LOOKUP -> pendingTarget = currentTarget
                        ScrollLookupTapPolicy.Decision.DELEGATE -> delegateGesture()
                        else -> Unit
                    }
                    currentTarget = null
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdTimeout)
                    policy?.cancel()
                    currentTarget = null
                    pendingTarget = null
                }
            }
        }

        override fun onStateChanged(state: Int) {
            if (state == TouchInteractionController.STATE_CLEAR) {
                handler.removeCallbacks(holdTimeout)
                policy?.cancel()
                delegateRequested = false
                val target = pendingTarget
                pendingTarget = null
                if (target != null) handler.post { captureOriginal(target) }
                scheduleRefresh()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            message("Scroll Translate lookup requires Android 14 or later.")
            disableSelf()
            return
        }
        operational = true
        // Samsung/One UI can trap all touch when touch-exploration routing is enabled.
        // Keep this service passive by default; never request touch exploration here.
        setRouting(false)
        policy = ScrollLookupTapPolicy(
            ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
            ViewConfiguration.getLongPressTimeout().toLong(),
        )
        touchController = getTouchInteractionController(Display.DEFAULT_DISPLAY)
        touchController?.registerCallback(mainExecutor, touchCallback)
        message("Chimahon lookup service ready. Screen touch remains fully normal.")
        scheduleRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val owner = event.packageName?.toString()
        if (owner != null && event.windowId >= 0) {
            windowOwners[event.windowId] = owner
            if (windowOwners.size > 64) {
                windowOwners.keys.firstOrNull()?.let(windowOwners::remove)
            }
        }
        if (operational) scheduleRefresh()
    }

    override fun onInterrupt() {
        shutdown()
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
        session.reset()
        pendingTarget = null
        overlay?.dismiss()
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
            val wm = requiredService<WindowManager>()
            val displayBounds = wm.currentWindowMetrics.bounds
            val currentWindows = windows.filter { it.displayId == Display.DEFAULT_DISPLAY }
            val googleWindows = currentWindows.filter { windowPackage(it) == GOOGLE_PACKAGE }
            googleVisible = googleWindows.isNotEmpty()
            keyboardVisible = currentWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            protectedWindows = currentWindows.mapNotNull { window ->
                val owner = windowPackage(window)
                if (owner == GOOGLE_PACKAGE || owner == packageName) return@mapNotNull null
                val protected = window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                    window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                    owner in EXCLUDED_PACKAGES
                if (!protected) return@mapNotNull null
                Rect().also { window.getBoundsInScreen(it) }.takeUnless { it.isEmpty }
            }
            targets = currentWindows.sortedByDescending { it.layer }.mapNotNull { window ->
                val owner = windowPackage(window) ?: return@mapNotNull null
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION ||
                    owner == packageName || owner in EXCLUDED_PACKAGES
                ) return@mapNotNull null
                val bounds = Rect().also { window.getBoundsInScreen(it) }
                if (bounds.isEmpty || !Rect.intersects(bounds, displayBounds)) return@mapNotNull null
                CaptureTarget(window.id, Rect(bounds), Rect(displayBounds))
            }
            googleControls = googleWindows.flatMap { smallClickableBounds(it.root, displayBounds) }
            if (locked()) {
                session.reset()
                pendingTarget = null
                overlay?.dismiss()
            }
            val competingService = requiredService<AccessibilityManager>()
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    it.resolveInfo.serviceInfo.name != javaClass.name &&
                        it.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
                }
            if (competingService && !warnedConflict) {
                warnedConflict = true
                message("Lookup touch gestures are disabled while another touch-exploration service is active.")
            }
            // Enabling this accessibility service is the explicit opt-in switch.
            // Do not require Google's overlay to expose a recognizable accessibility window:
            // Samsung/Google builds can represent Circle to Search differently.
            // Do not arm global touch routing automatically.
            // On Samsung/One UI this can block the whole screen.
            setRouting(false)
        }.onFailure {
            // Fail open: do not leave an invisible touch interceptor active after an error.
            setRouting(false)
        }
    }

    private fun windowPackage(window: AccessibilityWindowInfo): String? =
        window.root?.packageName?.toString() ?: windowOwners[window.id]

    private fun smallClickableBounds(root: AccessibilityNodeInfo?, display: Rect): List<Rect> {
        if (root == null) return emptyList()
        val result = mutableListOf<Rect>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count++ < 200) {
            val node = queue.removeFirst()
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (node.isVisibleToUser && node.isClickable && !bounds.isEmpty &&
                bounds.width().toLong() * bounds.height() < display.width().toLong() * display.height() / 5
            ) result.add(bounds)
            for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
        }
        return result
    }

    private fun eligibleTarget(x: Float, y: Float): CaptureTarget? {
        if (!routing || keyboardVisible || locked() ||
            session.state != ScrollLookupSession.State.IDLE
        ) return null
        val target = targets.firstOrNull { it.bounds.contains(x.toInt(), y.toInt()) } ?: return null
        val edge = (24 * resources.displayMetrics.density).toInt()
        val bar = (48 * resources.displayMetrics.density).toInt()
        val screen = target.display
        if (x < screen.left + edge || x >= screen.right - edge ||
            y < screen.top + bar || y >= screen.bottom - bar ||
            googleControls.any { it.contains(x.toInt(), y.toInt()) } ||
            protectedWindows.any { it.contains(x.toInt(), y.toInt()) }
        ) return null
        return target
    }

    private fun delegateGesture() {
        handler.removeCallbacks(holdTimeout)
        policy?.cancel()
        currentTarget = null
        pendingTarget = null
        if (delegateRequested) return
        delegateRequested = true
        runCatching { touchController?.requestDelegating() }.onFailure { setRouting(false) }
    }

    private fun setRouting(enabled: Boolean) {
        val info = serviceInfo ?: return
        val platformEnabled = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
        if (enabled == routing && enabled == platformEnabled) return
        info.flags = if (enabled) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
        }
        serviceInfo = info
        routing = enabled
        if (!enabled) {
            handler.removeCallbacks(holdTimeout)
            policy?.cancel()
        }
    }

    private fun captureOriginal(target: CaptureTarget) {
        if (!operational || locked()) return
        val ticket = session.begin() ?: return
        setRouting(false)
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
                        if (locked() || targets.firstOrNull { Rect.intersects(it.bounds, target.bounds) } != target) {
                            captureFailed(ticket, "The app or orientation changed; tap again.")
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
                                handler.postDelayed({ scheduleRefresh() }, 260)
                            },
                            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            showOriginalSnapshot = true,
                            dismissOnEmptyTap = true,
                        ).also { overlay = it }
                        controller.show(frame)
                    } catch (_: Exception) {
                        overlay?.dismiss()
                        session.reset()
                        message("Original capture failed. Protected screens cannot be captured.")
                        scheduleRefresh()
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    captureFailed(ticket, "Original capture failed (Android error $errorCode). No translated-screen fallback was used.")
                }
            })
        } catch (_: Exception) {
            captureFailed(ticket, "Android could not capture the original app window.")
        }
    }

    private fun captureFailed(ticket: Long, text: String) {
        if (!session.isPending(ticket)) return
        session.reset()
        blockedUntil = SystemClock.uptimeMillis() + 600
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

    private fun triggerManualLookup() {
        if (!operational || locked() || session.state != ScrollLookupSession.State.IDLE) return
        refreshWindows()
        val target = targets.firstOrNull() ?: run {
            message("No capturable app window found.")
            return
        }
        captureOriginal(target)
    }

    private fun shutdown() {
        if (activeInstance === this) activeInstance = null
        operational = false
        setRouting(false)
        session.reset()
        pendingTarget = null
        currentTarget = null
        handler.removeCallbacksAndMessages(null)
        refreshScheduled = false
        touchController?.unregisterCallback(touchCallback)
        touchController = null
        overlay?.release()
        overlay = null
        windowOwners.clear()
    }

    private data class CaptureTarget(val id: Int, val bounds: Rect, val display: Rect)

    companion object {
        @Volatile
        private var activeInstance: ScrollTranslateLookupAccessibilityService? = null

        fun requestManualLookup(): Boolean {
            val service = activeInstance ?: return false
            service.handler.post { service.triggerManualLookup() }
            return true
        }

        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        val EXCLUDED_PACKAGES = setOf(
            GOOGLE_PACKAGE,
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
