package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class PopupPlacementPolicyTest {

    @TestFactory
    fun yomitanStylePlacementChecks(): List<DynamicTest> = popupPlacementChecks().map { (name, run) ->
        DynamicTest.dynamicTest(name) { run() }
    }
}

internal fun popupPlacementChecks(): List<Pair<String, () -> Unit>> {
    fun overlaps(result: PopupPlacement, source: PopupSourceRect): Boolean {
        return result.x < source.right &&
            result.x + result.width > source.left &&
            result.y < source.bottom &&
            result.y + result.height > source.top
    }

    return listOf(
        "horizontal prefers below when there is room" to {
            val source = PopupSourceRect(100f, 200f, 300f, 260f)
            val result = PopupPlacementPolicy.horizontal(
                source = source,
                preferredWidth = 500f,
                preferredHeight = 400f,
                viewportWidth = 1080f,
                viewportHeight = 2200f,
                padding = 24f,
                gap = 16f,
                preferBelow = true,
            )
            check(result.after)
            check(result.y >= source.bottom + 16f)
            check(!overlaps(result, source))
        },
        "horizontal switches above when below overflows more" to {
            val source = PopupSourceRect(100f, 1600f, 500f, 1680f)
            val result = PopupPlacementPolicy.horizontal(
                source = source,
                preferredWidth = 700f,
                preferredHeight = 900f,
                viewportWidth = 1080f,
                viewportHeight = 2200f,
                padding = 24f,
                gap = 16f,
                preferBelow = true,
            )
            check(!result.after)
            check(result.y + result.height <= source.top - 16f + 0.01f)
            check(!overlaps(result, source))
        },
        "horizontal shrinks instead of crossing source" to {
            val source = PopupSourceRect(100f, 980f, 900f, 1060f)
            val result = PopupPlacementPolicy.horizontal(
                source = source,
                preferredWidth = 900f,
                preferredHeight = 1500f,
                viewportWidth = 1080f,
                viewportHeight = 2200f,
                padding = 24f,
                gap = 16f,
                preferBelow = true,
            )
            check(result.height < 1500f)
            check(!overlaps(result, source))
        },
        "vertical switches side and does not overlap" to {
            val source = PopupSourceRect(850f, 300f, 920f, 900f)
            val result = PopupPlacementPolicy.vertical(
                source = source,
                preferredWidth = 600f,
                preferredHeight = 1000f,
                viewportWidth = 1080f,
                viewportHeight = 2200f,
                padding = 24f,
                gap = 16f,
                preferRight = true,
            )
            check(!result.after)
            check(result.x + result.width <= source.left - 16f + 0.01f)
            check(!overlaps(result, source))
        },
    )
}
