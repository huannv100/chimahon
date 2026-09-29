# Floating original OCR / Google translation coexistence patch

This patch responds to the physical-device report that opening Google Scroll and Translate closes Chimahon OCR, and opening OCR closes Google. The source had two concrete problems: the original OCR overlay requested window focus, and AccessibilityService.onInterrupt called full shutdown. These have been removed. They are plausible causes, not proof of Google's proprietary behavior on the phone.

## Changes

- Keep the floating Screen OCR button, not a tile or full-screen activation gesture.
- Original OCR uses a non-focusable but touchable TYPE_ACCESSIBILITY_OVERLAY. Dictionary content remains in the same host window.
- Feedback interruption no longer closes the OCR or stops the service.
- Remove all obsolete global touch-controller code and remove the touch-exploration capability from XML.
- Close using empty space or the always-visible Close OCR button. Android Back is not intercepted and can dismiss Google.
- Reuse the accessibility button when the ordinary Screen OCR entry is opened while this service is connected, avoiding a second MediaProjection permission flow. A legacy running Chimahon capture is stopped when the accessibility service starts.
- Only display-size changes or locking invalidate an existing snapshot; unrelated configuration/window events do not intentionally close it.

## Setup

Turn OFF the previous Chimahon accessibility test service before updating. Back up the preview app's dictionaries/settings before uninstalling if Android reports a signing mismatch. Install the focusfix.5 ARM64 preview, enable its accessibility service and use its floating OCR button. Stop any older Screen OCR recording session. Start Google Scroll and Translate once, then use the floating OCR button for dictionary inspection. No additional Start recording/casting consent dialog should appear for this path. Do not run multiple old test versions' accessibility services simultaneously.

Google translation may be visually covered by the original snapshot during dictionary inspection; that is different from its session being closed. The snapshot is still frozen and modal. Empty-space tap or Close OCR returns to live content. The service sends no Play, Pause, Back or Google-dismiss command.

## Validation boundaries

The ARM64 workflow runs static safety regressions, assembles the app and executes app unit tests including four window-flag regressions before publishing this APK. Read the actual run results rather than assuming success. The old qa/android gesture probe was written for the retired global-tap prototype and does not validate this floating-button implementation. Neither that probe nor unit tests demonstrate interoperability with Google's actual UI on a Galaxy S24 Ultra. Device verification of original capture, dictionary clicks, continued Google translation and repeated open/close is still required.
