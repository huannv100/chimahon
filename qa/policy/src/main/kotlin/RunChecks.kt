package eu.kanade.tachiyomi.ui.dictionary

fun main() {
    val checks = scrollLookupPolicyChecks()
    checks.forEach { (name, run) ->
        run()
        println("PASS: $name")
    }
    println("${checks.size} checks passed (including 10000 randomized gestures).")
}
