# Native Android platform probe

Compiles the production accessibility service and gesture policy. Only the Google package literal is replaced in a generated QA source copy so a synthetic translation overlay can exercise routing without Google apps. A minimal screenshot viewer substitutes for the Chimahon dictionary UI.

These are platform integration checks, NOT end-to-end Samsung/Google/OCR tests. No probe APK is published to users. The runner installs three disposable test apps in the emulator, explicitly enables the test accessibility service, verifies swipe delegation, tap consumption, underlying-window pixels, repeated open/dismiss cycles, and service disable recovery. Runtime logs are retained.
