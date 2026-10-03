package eu.kanade.tachiyomi.ui.dictionary

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Coordinates are density-independent pixels. Drawn OCR boxes are not enlarged. */
internal data class OcrTapRect(
    val blockIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val valid: Boolean get() = listOf(left, top, right, bottom).all { it.isFinite() } &&
        right > left && bottom > top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun dx(x: Float): Float = max(max(left - x, 0f), x - right)
    fun dy(y: Float): Float = max(max(top - y, 0f), y - bottom)
}

internal sealed interface OcrTapResult {
    data class Hit(val blockIndex: Int, val x: Float, val y: Float) : OcrTapResult
    data object KeepOpen : OcrTapResult
    data object Empty : OcrTapResult
}

/** Exact text wins; otherwise snap to the nearest line, never to a random expanded box. */
internal fun resolveOcrTap(rectangles: List<OcrTapRect>, x: Float, y: Float): OcrTapResult {
    if (!x.isFinite() || !y.isFinite()) return OcrTapResult.KeepOpen
    val valid = rectangles.filter { it.valid }
    fun hit(rect: OcrTapRect) = OcrTapResult.Hit(
        rect.blockIndex,
        x.coerceIn(rect.left, rect.right),
        y.coerceIn(rect.top, rect.bottom),
    )
    valid.filter { it.contains(x, y) }
        .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
        ?.let { return hit(it) }

    val near = valid.filter {
        it.dx(x) <= max(18f, (48f - (it.right - it.left)) / 2f) &&
            it.dy(y) <= max(18f, (48f - (it.bottom - it.top)) / 2f)
    }.sortedBy { it.dx(x) * it.dx(x) + it.dy(y) * it.dy(y) }
    val best = near.firstOrNull()
    if (best != null) {
        val second = near.getOrNull(1)
        if (second != null) {
            val d1 = sqrt(best.dx(x) * best.dx(x) + best.dy(y) * best.dy(y))
            val d2 = sqrt(second.dx(x) * second.dx(x) + second.dy(y) * second.dy(y))
            // Between two nearly equally close lines, keep OCR open for another tap.
            if (abs(d1 - d2) < 2f) return OcrTapResult.KeepOpen
        }
        return hit(best)
    }
    // A near-miss outside the selection padding must not dismiss the whole snapshot.
    return if (valid.any { it.dx(x) <= 28f && it.dy(y) <= 28f }) {
        OcrTapResult.KeepOpen
    } else {
        OcrTapResult.Empty
    }
}

/** Only the small floating button uses this gesture policy. No global input handling. */
internal class FloatingOcrButtonGesture(private val slopPx: Float, private val holdMs: Long) {
    enum class Action { NONE, CLICK, DRAG, HIDE }
    private var active = false
    private var dragging = false
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L

    init {
        require(slopPx.isFinite() && slopPx >= 0f)
        require(holdMs > 0)
    }

    fun down(x: Float, y: Float, time: Long) {
        active = x.isFinite() && y.isFinite()
        dragging = false
        startX = x
        startY = y
        downTime = time
    }

    fun move(x: Float, y: Float): Action {
        if (!active) return Action.NONE
        if (!x.isFinite() || !y.isFinite()) {
            cancel()
            return Action.NONE
        }
        dragging = dragging || abs(x - startX) > slopPx || abs(y - startY) > slopPx
        return if (dragging) Action.DRAG else Action.NONE
    }

    fun hold(time: Long): Action {
        if (!active || dragging || time - downTime < holdMs) return Action.NONE
        active = false
        return Action.HIDE
    }

    fun up(x: Float, y: Float, time: Long): Action {
        if (!active) return Action.NONE
        move(x, y)
        val result = when {
            !active || dragging || time < downTime -> Action.NONE
            time - downTime >= holdMs -> Action.HIDE
            else -> Action.CLICK
        }
        cancel()
        return result
    }

    fun cancel() {
        active = false
        dragging = false
    }
}
