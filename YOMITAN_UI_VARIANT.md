# Chimahon Huan Yomitan UI variant

Based on the user-confirmed Huan v2.4.6 headword fixes and subsequent popup placement work. This is an Android adaptation of Yomitan's source-range and constrained popup-placement approach, not the Yomitan extension runtime.

## Build

Run `python3 qa/prepare_yomitan_build.py` before Gradle. The idempotent preparation script changes the application identity, bundles `headword-selection.js` into the `renderer.js` actually inlined by Android, and connects recursive matched ranges through the real WebView screen origin. Its exact replacement guards fail on unexpected upstream source changes. Generated sources and hashes are saved in the CI verification artifact.

Package: `app.chimahon.huan.yomitan.beta`. Label: `Chimahon Huan Yomitan`. Version code: `2461001`. Install alongside previous Huan and official versions; those installations and data remain unchanged. Import dictionaries/settings into the new app. Do not enable multiple competing OCR accessibility services at once.

## Behavior

Popup placement tries source rectangles and their union; it avoids selected text, changes sides and reduces the available height/width instead of clamping across a word. Extremely small/no-space layouts can return a zero-sized axis rather than cover the source. Headword taps scan from the actual glyph, exclude ruby readings/tags, and provide the matched range to recursive popups. Native text selections are preserved across pointerdown/click collapse. The existing native dictionary lookup, OCR capture and Google integration are retained.

## Validation scope

JUnit layout regressions include the reported screenshot proportions, multi-line selection, small viewports, preference checks, and 20,000 randomized layouts. Chromium checks exercise actual generated headword DOM and touch/click handling, with URI dispatch captured in a test sink. These tests do not emulate the proprietary Google/Samsung UI or validate native dictionary results on a physical S24 Ultra. Device acceptance remains required.

## Signing

CI produces a verified intermediate debug-signed APK. The delivered APK is signed separately with the owner's private key; that key must never be committed or uploaded as a public CI artifact. Use the same private signing key and increasing version codes for future in-place updates of this new package. A new key cannot update an old unrelated-signature Huan installation.

## Attribution

Yomitan reference: `yomidevs/yomitan` commit `67db60ddc2cbd7b5172d777c117e3201d7ddff0f`, `ext/js/app/popup.js`, `ext/js/app/frontend.js`, and `ext/js/language/text-scanner.js`. Yomitan Authors, GPL-3.0-or-later. Existing project licenses apply.
