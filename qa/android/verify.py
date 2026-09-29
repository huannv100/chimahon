"""Disposable emulator checks. Tests native routing/capture, not Google or dictionary OCR."""
import pathlib
import re
import subprocess
import time


def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], text=True, timeout=30)


def logs():
    return adb("logcat", "-d", "-s", "ScrollProbe:I", "AndroidRuntime:E", "*:S")


def until(predicate, description, seconds=12):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate(logs()):
            print("PASS:", description, flush=True)
            return
        time.sleep(0.25)
    raise AssertionError(description + "\n" + logs())


service = "org.chimahon.qa.probe/eu.kanade.tachiyomi.ui.dictionary.ScrollTranslateLookupAccessibilityService"
try:
    for name in ("fixture", "translator", "probe"):
        apk = pathlib.Path("qa/android") / name / "build/outputs/apk/debug" / (name + "-debug.apk")
        print(adb("install", "-r", apk))
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", service)
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    adb("shell", "appops", "set", "org.chimahon.qa.translator", "SYSTEM_ALERT_WINDOW", "allow")
    adb("shell", "am", "start", "-W", "-n", "org.chimahon.qa.fixture/.FixtureActivity")
    adb("shell", "am", "start", "-W", "-n", "org.chimahon.qa.translator/.TranslatorActivity")
    adb("shell", "am", "start", "-W", "-n", "org.chimahon.qa.fixture/.FixtureActivity")
    time.sleep(2)
    width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
    adb("logcat", "-c")
    # Right-hand strip is uncovered by the synthetic translation window.
    adb("shell", "input", "swipe", int(width * .85), int(height * .8), int(width * .85), int(height * .3), 500)
    until(lambda text: "SCROLL " in text, "native swipe reaches original app")
    assert "OPEN " not in logs(), "Swipe must not open lookup"
    print("PASS: swipe does not trigger lookup", flush=True)
    for cycle in range(3):
        adb("shell", "input", "tap", width // 2, height // 2)
        until(lambda text: text.count("OPEN pixel=") == cycle + 1, f"tap opens lookup (cycle {cycle + 1})")
        text = logs()
        assert "OPEN pixel=ff123456" in text, "Capture must contain ORIGINAL pixels, not the green overlay"
        assert "APP_CLICK" not in text and "TRANSLATION_CLICK" not in text, "Activation tap leaked to another app"
        print("PASS: original pixels captured and activation tap consumed", flush=True)
        adb("shell", "input", "tap", int(width * .8), int(height * .8))
        until(lambda text: text.count("CLOSE") == cycle + 1, "empty tap closes snapshot")
        time.sleep(.6)
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", "null")
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "0")
    time.sleep(1)
    adb("shell", "input", "tap", int(width * .85), int(height * .5))
    until(lambda text: "APP_CLICK" in text, "normal app taps recover after disabling service")
    print("ANDROID PLATFORM PROBE PASSED", flush=True)
finally:
    pathlib.Path("android-probe-logcat.txt").write_text(adb("logcat", "-d"))
    pathlib.Path("android-probe-accessibility.txt").write_text(adb("shell", "dumpsys", "accessibility"))
    pathlib.Path("android-probe-windows.txt").write_text(adb("shell", "dumpsys", "window", "windows"))
