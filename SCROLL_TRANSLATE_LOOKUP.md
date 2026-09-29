# Scroll Translate + original-text lookup (experimental)

Based on Chimahon v2.4.6. Old Douyin/Quick Translate code is not included.

## Setup

Use the preview APK, import dictionaries, and select the desired active language profile (Chinese for Chinese OCR). In Android Settings > Accessibility > Installed apps/services, enable this Chimahon service and read its description. Android 14+ is required. Start Google's Scroll and Translate normally. Do not start Chimahon's separate MediaProjection Screen OCR session for this integration; it is not needed.

A short tap in the content area is consumed and captures the original APPLICATION WINDOW with AccessibilityService.takeScreenshotOfWindow. It does not wait for Google to hide translation, and does not inject a tap, Back, play or pause action. TouchInteractionController delegates swipes to Android. No Google TYPE_VIEW_CLICKED event is required.

Chimahon displays the original snapshot with OCR hit boxes. Tap a recognized word to use the existing dictionary popup. Tap empty space to dismiss lookup. Google's translation session is not deliberately stopped. System edges, keyboards and detected small Google controls are not activation targets.

## Important limits

- Lookup is a **frozen, modal snapshot**, not a live transparent screen. The underlying video is not commanded to pause/play, but its displayed image is frozen during dictionary inspection. Dismiss lookup before operating the underlying app.
- The service requests Android touch exploration while a Google window and eligible app window are visible. This is not a passive all-touch listener. It avoids coexisting with another touch-exploration service. Turn off the service in Accessibility settings to disable the integration.
- Window visibility and layers on the actual Samsung/Google build still require device validation. If Google or the target app does not expose an eligible window, the service leaves touch routing disabled. Protected screens fail without falling back to the translated screen.
- Original snapshots are memory-only in the integration. The existing selected OCR engine may use a network service; choose an on-device engine when needed.
- Google UI detection uses the Google package and visible windows, not an undocumented Scroll Translate API. Other Google overlays can also satisfy this condition. Google controls with accessible bounds are excluded, but not every Google build exposes identical nodes.

## Validation

23 executable gesture/session checks include stationary taps, jitter, vertical/horizontal/diagonal swipes, a swipe returning to its origin, multi-touch, hold timeout, cancellation, invalid coordinates, duplicate captures, late callbacks, 100 repeated lookup cycles and 10,000 seeded randomized swipe sequences. These check policy logic, not a physical phone or proprietary Google UI.

CI builds the preview and runs unit tests before uploading APKs. Repository-wide formatting is a separate visible check so unrelated upstream formatting failures no longer prevent compilation from running. Passing compilation or unit tests must not be described as an end-to-end S24 Ultra test.

Device acceptance checks still required: Google automatic translation remains active; a content tap shows ORIGINAL Chinese OCR; paused video stays paused; playing video continues underneath; swipes do not launch lookup; a word opens definitions; one empty tap closes lookup; repeating this cycle works; Google language controls and Android navigation remain usable; rotation/lock and capture failure recover without trapping touch input.
