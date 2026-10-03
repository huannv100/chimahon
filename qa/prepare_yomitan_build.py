"""Prepare reproducible Yomitan UI variant from the pinned Huan source.
No binary APK patching; this runs before source tests/compilation.
Private signing keys are never generated, embedded or uploaded by CI.
"""
from pathlib import Path

def replace_once(text, before, after):
    if after in text: return text
    if text.count(before) != 1:
        raise RuntimeError('Source changed; review patch target: '+before[:100])
    return text.replace(before,after,1)

gradle=Path('app/build.gradle.kts')
s=gradle.read_text()
s=replace_once(s, 'applicationId = "app.chimahon.huan"', 'applicationId = "app.chimahon.huan.yomitan"')
gradle.write_text(s)
manifest=Path('app/src/main/AndroidManifest.xml')
s=manifest.read_text().replace('android:label="Chimahon Huan"','android:label="Chimahon Huan Yomitan"')
manifest.write_text(s)

# The Android bootstrap inlines renderer.js; editing only index.html is not
# sufficient. Bundle the separate tested adapter into the actual loaded asset.
renderer=Path('chimahon/src/main/assets/dictionary/renderer.js')
addon=renderer.with_name('headword-selection.js').read_text()
s=renderer.read_text()
marker='\n/* HUAN_YOMITAN_HEADWORD_ADAPTER */\n'
if marker in s: s=s.split(marker)[0]
renderer.write_text(s+marker+addon)

# Convert matched DOM client rectangles through the real WebView origin. The
# old child popup used a guessed chrome height; that obscures recursive words.
file=Path('app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/OcrLookupPopup.kt')
s=file.read_text()
s=replace_once(s, '    val anchorHeight: Float = 0f,\n    val deferredResult:',
'''    val anchorHeight: Float = 0f,
    val sourceRects: List<PopupSourceRect> = emptyList(),
    val absoluteAnchor: Boolean = false,
    val deferredResult:''')
s=replace_once(s, '                        val cssScale = density.density\n                        childPopupRequest = RecursivePopupRequest(',
'''                        val location = IntArray(2)
                        webView.getLocationOnScreen(location)
                        val cssViewport = rect?.optDouble("viewportWidth", 0.0) ?: 0.0
                        val cssScale = if (cssViewport > 0.0 && webView.width > 0) {
                            webView.width / cssViewport.toFloat()
                        } else density.density
                        val pieces = rect?.optJSONArray("rects")
                        val matchedRects = (0 until (pieces?.length() ?: 0)).mapNotNull { index ->
                            val r = pieces?.optJSONObject(index) ?: return@mapNotNull null
                            val l = r.optDouble("left", Double.NaN).toFloat()
                            val t = r.optDouble("top", Double.NaN).toFloat()
                            val right = r.optDouble("right", Double.NaN).toFloat()
                            val bottom = r.optDouble("bottom", Double.NaN).toFloat()
                            if (!listOf(l, t, right, bottom).all { it.isFinite() } || right <= l || bottom <= t) {
                                return@mapNotNull null
                            }
                            PopupSourceRect(location[0] + l * cssScale, location[1] + t * cssScale,
                                location[0] + right * cssScale, location[1] + bottom * cssScale)
                        }
                        childPopupRequest = RecursivePopupRequest(''')
s=replace_once(s, 'tapX = (rect?.optDouble("x")?.toFloat() ?: x ?: 0f) * cssScale,',
                 'tapX = location[0] + (rect?.optDouble("x")?.toFloat() ?: x ?: 0f) * cssScale,')
s=replace_once(s, 'tapY = (rect?.optDouble("y")?.toFloat() ?: y ?: 0f) * cssScale,',
                 'tapY = location[1] + (rect?.optDouble("y")?.toFloat() ?: y ?: 0f) * cssScale,')
s=replace_once(s, '                            anchorHeight = (rect?.optDouble("height")?.toFloat() ?: 0f) * cssScale,',
'''                            anchorHeight = (rect?.optDouble("height")?.toFloat() ?: 0f) * cssScale,
                            sourceRects = matchedRects,
                            absoluteAnchor = true,''')
s=replace_once(s, 'val tapScreenX = layoutResult.x + (request.tapX ?: layoutResult.widthPx / 2f)',
'val tapScreenX = if (request.absoluteAnchor) request.tapX ?: layoutResult.x else layoutResult.x + (request.tapX ?: layoutResult.widthPx / 2f)')
s=replace_once(s, 'val tapScreenY = layoutResult.y + closeChromePx + (request.tapY ?: layoutResult.heightPx / 2f)',
'val tapScreenY = if (request.absoluteAnchor) request.tapY ?: layoutResult.y else layoutResult.y + closeChromePx + (request.tapY ?: layoutResult.heightPx / 2f)')
s=replace_once(s, '            anchorHeight = request.anchorHeight,\n            isVertical =',
'            anchorHeight = request.anchorHeight,\n            sourceRects = request.sourceRects,\n            isVertical =')
file.write_text(s)
print('Prepared app.chimahon.huan.yomitan.beta; bundled headword adapter and actual WebView source rectangles.')
