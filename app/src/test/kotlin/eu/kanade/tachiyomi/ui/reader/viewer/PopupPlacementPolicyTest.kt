package eu.kanade.tachiyomi.ui.reader.viewer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopupPlacementPolicyTest {

    @Test
    fun horizontal_prefersBelow_whenThereIsRoom() {
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
        assertTrue(result.after)
        assertTrue(result.y >= source.bottom + 16f)
        assertFalse(overlaps(result, source))
    }

    @Test
    fun horizontal_switchesAbove_whenBelowOverflowsMore() {
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
        assertFalse(result.after)
        assertTrue(result.y + result.height <= source.top - 16f + 0.01f)
        assertFalse(overlaps(result, source))
    }

    @Test
    fun horizontal_shrinksInsteadOfCrossingSource() {
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
        assertTrue(result.height < 1500f)
        assertFalse(overlaps(result, source))
    }

    @Test
    fun vertical_switchesSide_andDoesNotOverlap() {
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
        assertFalse(result.after)
        assertTrue(result.x + result.width <= source.left - 16f + 0.01f)
        assertFalse(overlaps(result, source))
    }

    private fun overlaps(result: PopupPlacement, source: PopupSourceRect): Boolean {
        return result.x < source.right &&
            result.x + result.width > source.left &&
            result.y < source.bottom &&
            result.y + result.height > source.top
    }
}
