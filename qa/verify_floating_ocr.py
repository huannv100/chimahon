"""Static safety regressions, not a substitute for a physical Samsung/Google test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/eu/kanade/tachiyomi/ui/dictionary'
service = (base / 'ScrollTranslateLookupAccessibilityService.kt').read_text()
overlay = (base / 'ScreenLookupOverlayController.kt').read_text()
entry = (base / 'ScreenLookupPermissionActivity.kt').read_text()
xml = ET.parse(root / 'app/src/main/res/xml/scroll_translate_lookup_accessibility_service.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
checks = {
    'no touch-exploration capability': xml.get(ns + 'canRequestTouchExplorationMode') == 'false',
    'no global touch controller': 'TouchInteractionController' not in service,
    'no generic touchscreen callback': 'override fun onMotionEvent' not in service,
    'no injected Back or gesture': 'performGlobalAction(' not in service and 'dispatchGesture(' not in service,
    'no projection session in integration': 'getMediaProjection(' not in service and 'createVirtualDisplay(' not in service,
    'capture original window': 'takeScreenshotOfWindow(target.id' in service,
    'feedback interruption does not shut down': 'shutdown()' not in re.search(r'override fun onInterrupt\(\) \{(.*?)\n    \}', service, re.S).group(1),
    'overlay uses tested window policy': 'ScreenLookupWindowPolicy.flagsFor(windowType)' in overlay,
    'explicit local close control': 'Text("Close OCR")' in overlay,
    'dictionary remains in host window': 'usePopup = false' in overlay,
    'legacy capture entry reuses accessibility button': entry.count('showButtonIfConnected()') >= 3,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'{len(checks)} static safety checks passed. Device interoperability is NOT tested here.')
