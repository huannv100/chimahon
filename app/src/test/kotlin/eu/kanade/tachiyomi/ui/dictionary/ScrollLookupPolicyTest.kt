package eu.kanade.tachiyomi.ui.dictionary

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ScrollLookupPolicyTest {
    @TestFactory
    fun gestureAndSessionChecks(): List<DynamicTest> = scrollLookupPolicyChecks().map { (name, run) ->
        DynamicTest.dynamicTest(name) { run() }
    }
}
