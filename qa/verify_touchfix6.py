"""Source wiring checks; executable gesture/geometry behavior is covered by unit tests."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/eu/kanade/tachiyomi/ui/dictionary'
service = (base / 'ScrollTranslateLookupAccessibilityService.kt').read_text()
overlay = (base / 'ScreenLookupOverlayController.kt').read_text()
canvas = (base / 'OcrOverlayComponents.kt').read_text()
checks = {
    'assist enabled only for original snapshots': 'forgivingTaps = showOriginalSnapshot' in overlay and 'forgivingTaps: Boolean = false' in canvas,
    'loading taps cannot accidentally close original OCR': 'allowEmptyTap = !showOriginalSnapshot || !isLoading' in overlay,
    'canvas uses tested tap resolver': 'resolveOcrTap(rectangles, offset.x / density, offset.y / density)' in canvas,
    'near-text guard cannot dismiss': 'OcrTapResult.KeepOpen -> Unit' in canvas,
    'long press uses platform timeout': 'ViewConfiguration.getLongPressTimeout()' in service,
    'gesture removal cancels timers': 'cancelFloatingGesture?.invoke()' in service and 'removeCallbacks(longPress)' in service,
    'long press hides only button': bool(re.search(r'setOnLongClickListener\s*\{\s*removeFloatingButton\(\)', service)),
    'existing in-app entry can restore button': 'fun showButtonIfConnected(): Boolean' in service,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'{len(checks)} touch wiring checks passed; these do not test physical phone interaction.')
