package eu.kanade.tachiyomi.ui.dictionary

import eu.kanade.tachiyomi.ui.reader.viewer.OcrLineGeometry
import eu.kanade.tachiyomi.ui.reader.viewer.OcrTextBlock
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.math.abs

class OcrMatchedSourceRectsTest {

    @TestFactory
    fun exactMatchedRects(): List<DynamicTest> = listOf(
        "single line match uses only matched characters" to {
            val block = OcrTextBlock(
                xmin = 0.1f,
                ymin = 0.1f,
                xmax = 0.5f,
                ymax = 0.15f,
                lines = listOf("准备工作"),
                lineGeometries = listOf(OcrLineGeometry(0.1f, 0.1f, 0.5f, 0.15f)),
            )
            val selection = OcrSelection(
                block = block,
                lookupString = "准备工作",
                sentence = "准备工作",
                sentenceOffset = 0,
                anchorX = 100f,
                anchorY = 200f,
                anchorWidth = 400f,
                anchorHeight = 100f,
            )
            val rects = ocrMatchedSourceRects(
                block, selection,
                activeMatchCount = 2,
                activeMatchOffset = 0,
                widthPx = 1000f,
                heightPx = 2000f,
            )
            check(rects.size == 1)
            val r = rects.single()
            check(close(r.left, 100f))
            check(close(r.right, 300f))
            check(close(r.top, 200f))
            check(close(r.bottom, 300f))
        },
        "multi line match produces separate source rectangles" to {
            val block = OcrTextBlock(
                xmin = 0.1f,
                ymin = 0.1f,
                xmax = 0.5f,
                ymax = 0.25f,
                lines = listOf("你好", "世界"),
                lineGeometries = listOf(
                    OcrLineGeometry(0.1f, 0.1f, 0.5f, 0.15f),
                    OcrLineGeometry(0.1f, 0.2f, 0.5f, 0.25f),
                ),
            )
            val selection = OcrSelection(
                block = block,
                lookupString = "好世",
                sentence = "你好 世界",
                sentenceOffset = 1,
                anchorX = 100f,
                anchorY = 200f,
                anchorWidth = 400f,
                anchorHeight = 300f,
            )
            val rects = ocrMatchedSourceRects(
                block, selection,
                activeMatchCount = 2,
                activeMatchOffset = 0,
                widthPx = 1000f,
                heightPx = 2000f,
            )
            check(rects.size == 2)
            check(close(rects[0].left, 300f))
            check(close(rects[0].right, 500f))
            check(close(rects[1].left, 100f))
            check(close(rects[1].right, 300f))
        },
        "no match yet falls back to full OCR block" to {
            val block = OcrTextBlock(
                xmin = 0.2f,
                ymin = 0.3f,
                xmax = 0.8f,
                ymax = 0.4f,
                lines = listOf("准备"),
            )
            val selection = OcrSelection(
                block = block,
                lookupString = "准备",
                sentence = "准备",
                sentenceOffset = 0,
                anchorX = 200f,
                anchorY = 600f,
                anchorWidth = 600f,
                anchorHeight = 200f,
            )
            val r = ocrMatchedSourceRects(
                block, selection,
                activeMatchCount = 0,
                activeMatchOffset = 0,
                widthPx = 1000f,
                heightPx = 2000f,
            ).single()
            check(close(r.left, 200f))
            check(close(r.top, 600f))
            check(close(r.right, 800f))
            check(close(r.bottom, 800f))
        },
    ).map { (name, run) -> DynamicTest.dynamicTest(name) { run() } }

    private fun close(a: Float, b: Float): Boolean = abs(a - b) < 0.1f
}
