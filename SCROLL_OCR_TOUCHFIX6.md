# Floating original OCR: touchfix.6

Based directly on the user-confirmed working focusfix.5 commit 7612c32ff91a187f5d1d3e8318ecf5683ecc7ffd. Google coexistence flags, capture method, dictionary host and no-global-touch design are preserved.

## Easier word taps

Original-snapshot OCR now expands the invisible hit region by 18dp around each recognized line, with a minimum 48dp target for very small text. Exact text hits take priority. Otherwise the closest line is selected and the coordinates are clamped to that line before character lookup. An ambiguous tap between nearly equidistant lines or a near miss within the 28dp guard keeps OCR open instead of closing it. Drawn text/boxes do not change size. A tap well away from text still closes OCR, as does Close OCR. While OCR is still loading, use Close OCR to leave; tapping not-yet-recognized text does not immediately dismiss the snapshot.

The shared canvas defaults remain unchanged for camera/video/reader consumers; assistance is explicitly enabled only for original snapshots. Its gesture callbacks now use up-to-date Compose state.

## Floating button

Short tap: original capture and OCR. Drag: move button. Stationary long press: hide the floating button only, using Android's long-press timeout. Release after a hold never launches OCR. Dragging, multi-touch, cancel, locking, rotation or removal cancels pending long-press work. Google translation and the accessibility service are not stopped by hiding the button.

To restore the hidden button, open Chimahon's normal Screen OCR entry. It reuses the connected accessibility service without starting another recording session. Turning this accessibility service off and on also shows it again.

## Validation and installation

24 new executable unit checks cover exact/near/ambiguous/distant taps, horizontal and vertical text, clamping, small targets, tap/hold/drag/cancel behavior and 2,000 randomized drags. These passed in a local Kotlin run; the ARM64 CI workflow also runs the app unit suite and existing focus/safety checks before publishing an APK. Read the completed CI result for this exact commit, not an older build. New behavior still needs a phone check by the user.

Version code 24606, version name 2.4.6-floating-ocr-touchfix.6. Keep the previous test accessibility service off during an update. This is a preview package (app.chimahon.beta). Back up/export settings and dictionaries before any uninstall required by a signing mismatch; do not remove the normal release unnecessarily.
