package org.chimahon.qa.probe

import android.app.Activity
import android.os.Bundle
import android.util.Log

/** Opens the freshly installed test package before the emulator enables its service. */
class ProbeSetupActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Log.i("ScrollProbe", "PROBE_PACKAGE_OPEN")
        finish()
    }
}
