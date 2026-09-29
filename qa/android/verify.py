"""Disposable emulator checks. Tests native routing/capture, not Google or dictionary OCR."""
import pathlib
import re
import subprocess
import time


def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], text=True, timeout=30, stderr=subprocess.STDOUT)


def logs():
    return adb("logcat", "-d", "-s", "ScrollProbe:I", "AndroidRuntime:E", "*:S")


def until(predicate, description, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate(logs()):
            print("PASS:", description, flush=True)
            return
        time.sleep(0.25)
    raise AssertionError(description + "\n" + logs())


def start(component):
    result = adb("shell", "am", "start", "-W", "-n", component)
    print(result, flush=True)
    assert "Error" not in result and "Status: ok" in result, result


def gesture(x1, y1, x2=None, y2=None, duration=60):
    # ADB `input` bypasses accessibility touch processing. Dispatch a gesture
    # through the Android accessibility injection path, as native CTS tests do.
    before = logs().count("INJECTION_COMPLETE")
    adb("shell", "am", "broadcast", "-a", "org.chimahon.qa.GESTURE",
        "-p", "org.chimahon.qa.probe", "--ef", "x1", float(x1), "--ef", "y1", float(y1),
        "--ef", "x2", float(x1 if x2 is None else x2),
        "--ef", "y2", float(y1 if y2 is None else y2), "--ei", "duration", duration)
    until(lambda text: text.count("INJECTION_COMPLETE") > before, "input gesture completed")


service = "org.chimahon.qa.probe/eu.kanade.tachiyomi.ui.dictionary.ScrollTranslateLookupAccessibilityService"
try:
    adb("logcat", "-c")
    adb("shell", "settings", "put", "global", "device_provisioned", "1")
    adb("shell", "settings", "put", "secure", "user_setup_complete", "1")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    for name in ("fixture", "translator", "probe"):
        apk = pathlib.Path("qa/android") / name / "build/outputs/apk/debug" / (name + "-debug.apk")
        print(adb("install", "-r", apk), flush=True)
    start("org.chimahon.qa.probe/.ProbeSetupActivity")
    time.sleep(2)
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", service)
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    print(adb("shell", "settings", "get", "secure", "enabled_accessibility_services"), flush=True)
    until(lambda text: "SERVICE_CONNECTED" in text, "test accessibility service actually connected")
    adb("shell", "appops", "set", "org.chimahon.qa.translator", "SYSTEM_ALERT_WINDOW", "allow")
    start("org.chimahon.qa.fixture/.FixtureActivity")
    start("org.chimahon.qa.translator/.TranslatorActivity")
    until(lambda text: "TRANSLATION_READY" in text, "synthetic translation overlay exists")
    start("org.chimahon.qa.fixture/.FixtureActivity")
    until(lambda text: "ROUTING true" in text, "production controller armed over fixture window")
    time.sleep(1)
    width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
    gesture(width * .85, height * .8, width * .85, height * .3, 400)
    until(lambda text: "SCROLL " in text, "native swipe reaches original app")
    assert "OPEN " not in logs(), "Swipe must not open lookup"
    assert "MOTION " in logs(), "Swipe must have gone through the production touch controller"
    print("PASS: swipe does not trigger lookup", flush=True)
    time.sleep(1)
    for cycle in range(3):
        gesture(width / 2, height / 2)
        until(lambda text: text.count("OPEN pixel=") == cycle + 1, f"tap opens lookup (cycle {cycle + 1})")
        text = logs()
        assert "OPEN pixel=ff123456" in text, "Capture must contain ORIGINAL pixels, not the green overlay"
        assert "APP_CLICK" not in text and "TRANSLATION_CLICK" not in text, "Activation tap leaked to another app"
        print("PASS: original pixels captured and activation tap consumed", flush=True)
        gesture(width * .8, height * .8)
        until(lambda text: text.count("CLOSE") == cycle + 1, "empty tap closes snapshot")
        time.sleep(.6)
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", "null")
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "0")
    time.sleep(1)
    adb("shell", "input", "tap", int(width * .85), int(height * .5))
    until(lambda text: "APP_CLICK" in text, "normal app taps recover after disabling service")
    print("ANDROID PLATFORM PROBE PASSED", flush=True)
finally:
    for name, args in {
        "logcat": ("logcat", "-d"),
        "accessibility": ("shell", "dumpsys", "accessibility"),
        "windows": ("shell", "dumpsys", "window", "windows"),
        "packages": ("shell", "dumpsys", "package", "org.chimahon.qa.probe"),
    }.items():
        text = adb(*args)
        pathlib.Path(f"android-probe-{name}.txt").write_text(text)
        if name == "accessibility":
            print(text, flush=True)
    print(logs(), flush=True)
