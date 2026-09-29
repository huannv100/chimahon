package eu.kanade.tachiyomi.ui.dictionary

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random

class ScreenOcrTouchPolicyTest {
    @TestFactory
    fun forgivingTextAndFloatingButtonChecks(): List<DynamicTest> = screenOcrTouchChecks().map { (name, run) ->
        DynamicTest.dynamicTest(name) { run() }
    }
}

internal fun screenOcrTouchChecks(): List<Pair<String, () -> Unit>> {
    val rect = OcrTapRect(0, 100f, 100f, 200f, 116f)
    fun checkHit(result: OcrTapResult, index: Int = 0) {
        check(result is OcrTapResult.Hit && result.blockIndex == index) { "Unexpected: $result" }
    }
    return listOf(
        "exact text hit" to { checkHit(resolveOcrTap(listOf(rect), 150f, 108f)) },
        "18dp near miss selects and clamps to text" to {
            val hit = resolveOcrTap(listOf(rect), 145f, 132f)
            check(hit == OcrTapResult.Hit(0, 145f, 116f))
        },
        "left padding clamps to first character" to {
            check(resolveOcrTap(listOf(rect), 83f, 108f) == OcrTapResult.Hit(0, 100f, 108f))
        },
        "right padding clamps to last character" to {
            check(resolveOcrTap(listOf(rect), 217f, 108f) == OcrTapResult.Hit(0, 200f, 108f))
        },
        "28dp guard does not dismiss" to {
            check(resolveOcrTap(listOf(rect), 150f, 142f) == OcrTapResult.KeepOpen)
        },
        "far empty area dismisses" to {
            check(resolveOcrTap(listOf(rect), 150f, 160f) == OcrTapResult.Empty)
        },
        "exact neighboring line outranks expanded earlier line" to {
            val second = OcrTapRect(1, 100f, 123f, 200f, 139f)
            checkHit(resolveOcrTap(listOf(rect, second), 150f, 127f), 1)
        },
        "nearest line wins independent of OCR order" to {
            val second = OcrTapRect(1, 100f, 144f, 200f, 160f)
            checkHit(resolveOcrTap(listOf(rect, second), 150f, 140f), 1)
            checkHit(resolveOcrTap(listOf(second, rect), 150f, 140f), 1)
        },
        "ambiguous midpoint stays open" to {
            val second = OcrTapRect(1, 100f, 144f, 200f, 160f)
            check(resolveOcrTap(listOf(rect, second), 150f, 130f) == OcrTapResult.KeepOpen)
        },
        "tiny glyph has minimum 48dp target" to {
            checkHit(resolveOcrTap(listOf(OcrTapRect(0, 100f, 100f, 104f, 104f)), 102f, 82f))
        },
        "vertical text uses same padding" to {
            check(resolveOcrTap(listOf(OcrTapRect(0, 100f, 100f, 116f, 200f)), 132f, 145f) ==
                OcrTapResult.Hit(0, 116f, 145f))
        },
        "invalid touch cannot dismiss" to {
            check(resolveOcrTap(listOf(rect), Float.NaN, 100f) == OcrTapResult.KeepOpen)
        },
        "invalid rectangles ignored" to {
            checkHit(resolveOcrTap(listOf(rect.copy(left = Float.NaN), rect), 150f, 108f))
        },
        "empty OCR permits ordinary close" to {
            check(resolveOcrTap(emptyList(), 100f, 100f) == OcrTapResult.Empty)
        },
        "small exact region beats overlapping large region" to {
            checkHit(resolveOcrTap(listOf(rect, OcrTapRect(1, 140f, 102f, 160f, 114f)), 150f, 108f), 1)
        },
        "dp geometry is invariant over phone density" to {
            for (density in listOf(1f, 2f, 3f, 4f)) {
                checkHit(resolveOcrTap(listOf(rect), 145f * density / density, 132f * density / density))
            }
        },
        "tap clicks only once" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            check(g.up(2f, 2f, 1100) == FloatingOcrButtonGesture.Action.CLICK)
            check(g.up(2f, 2f, 1101) == FloatingOcrButtonGesture.Action.NONE)
        },
        "stationary long press hides with no release click" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            check(g.hold(1499) == FloatingOcrButtonGesture.Action.NONE)
            check(g.hold(1500) == FloatingOcrButtonGesture.Action.HIDE)
            check(g.up(0f, 0f, 1600) == FloatingOcrButtonGesture.Action.NONE)
        },
        "delayed hold callback still suppresses click" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            check(g.up(0f, 0f, 1700) == FloatingOcrButtonGesture.Action.HIDE)
            check(g.hold(1701) == FloatingOcrButtonGesture.Action.NONE)
        },
        "drag cancels hold and click even after return to start" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            check(g.move(20f, 0f) == FloatingOcrButtonGesture.Action.DRAG)
            g.move(0f, 0f)
            check(g.hold(1600) == FloatingOcrButtonGesture.Action.NONE)
            check(g.up(0f, 0f, 1700) == FloatingOcrButtonGesture.Action.NONE)
        },
        "cancel removes long press and click" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            g.cancel()
            check(g.hold(1600) == FloatingOcrButtonGesture.Action.NONE)
            check(g.up(0f, 0f, 1700) == FloatingOcrButtonGesture.Action.NONE)
        },
        "small finger jitter still allows long press" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000)
            g.move(3f, -4f)
            check(g.hold(1500) == FloatingOcrButtonGesture.Action.HIDE)
        },
        "new touch after hidden button restored is independent" to {
            val g = FloatingOcrButtonGesture(8f, 500)
            g.down(0f, 0f, 1000); g.hold(1500)
            g.down(10f, 10f, 2000)
            check(g.up(10f, 10f, 2100) == FloatingOcrButtonGesture.Action.CLICK)
        },
        "randomized drag cannot hide or click" to {
            val random = Random(6100)
            repeat(2000) {
                val g = FloatingOcrButtonGesture(8f, 500)
                g.down(100f, 100f, 1000)
                g.move(120f + random.nextFloat() * 100, 100f)
                check(g.hold(2000) == FloatingOcrButtonGesture.Action.NONE)
                check(g.up(100f, 100f, 2100) == FloatingOcrButtonGesture.Action.NONE)
            }
        },
    )
}
