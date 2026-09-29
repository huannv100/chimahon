package eu.kanade.tachiyomi.ui.dictionary

import kotlin.random.Random

internal fun scrollLookupPolicyChecks(): List<Pair<String, () -> Unit>> {
    val cases = mutableListOf<Pair<String, () -> Unit>>()
    fun test(name: String, block: () -> Unit) {
        cases.add(name to block)
    }
    val wait = ScrollLookupTapPolicy.Decision.WAIT
    val lookup = ScrollLookupTapPolicy.Decision.LOOKUP
    val delegate = ScrollLookupTapPolicy.Decision.DELEGATE
    val ignore = ScrollLookupTapPolicy.Decision.IGNORE
    fun policy() = ScrollLookupTapPolicy(12f, 450)

    test("stationary short tap opens lookup") {
        val p = policy()
        check(p.down(100f, 200f, 0, true) == wait)
        check(p.up(100f, 200f, 100) == lookup)
    }
    test("small thumb jitter remains a tap") {
        val p = policy()
        p.down(100f, 200f, 0, true)
        check(p.move(105f, 205f, 20) == wait)
        check(p.up(106f, 203f, 90) == lookup)
    }
    test("vertical swipe delegates") {
        val p = policy()
        p.down(100f, 200f, 0, true)
        check(p.move(100f, 180f, 20) == delegate)
        check(p.up(100f, 100f, 100) == ignore)
    }
    test("horizontal swipe delegates") {
        val p = policy()
        p.down(100f, 200f, 0, true)
        check(p.move(120f, 200f, 20) == delegate)
    }
    test("diagonal distance uses circular slop") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.move(9f, 9f, 20) == delegate)
    }
    test("swipe returning to origin never becomes tap") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.move(30f, 0f, 20) == delegate)
        check(p.up(0f, 0f, 80) == ignore)
    }
    test("fast swipe with no MOVE still not tap") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.up(30f, 0f, 20) == delegate)
    }
    test("second finger delegates instead of OCR") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.move(0f, 0f, 20, 2) == delegate)
        check(p.up(0f, 0f, 50) == ignore)
    }
    test("hold timeout delegates while finger remains down") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.timeout() == delegate)
        check(p.up(0f, 0f, 500) == ignore)
    }
    test("late UP cannot trigger OCR") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        check(p.up(0f, 0f, 450) == delegate)
    }
    test("ineligible region is delegated from DOWN") {
        val p = policy()
        check(p.down(0f, 0f, 0, false) == delegate)
        check(p.up(0f, 0f, 30) == ignore)
    }
    test("cancelled gesture cannot trigger") {
        val p = policy()
        p.down(0f, 0f, 0, true)
        p.cancel()
        check(p.up(0f, 0f, 40) == ignore)
    }
    test("a second independent tap can trigger") {
        val p = policy()
        repeat(2) {
            check(p.down(0f, 0f, it * 200L, true) == wait)
            check(p.up(0f, 0f, it * 200L + 100) == lookup)
        }
    }
    test("invalid coordinates fail open") {
        val p = policy()
        check(p.down(Float.NaN, 0f, 0, true) == delegate)
        p.down(0f, 0f, 0, true)
        check(p.move(Float.POSITIVE_INFINITY, 0f, 20) == delegate)
    }
    test("invalid event time fails open") {
        val p = policy()
        p.down(0f, 0f, 100, true)
        check(p.up(0f, 0f, 99) == delegate)
    }
    test("invalid configuration rejected") {
        check(runCatching { ScrollLookupTapPolicy(0f, 100) }.isFailure)
        check(runCatching { ScrollLookupTapPolicy(1f, 0) }.isFailure)
    }
    test("one screenshot request at a time") {
        val s = ScrollLookupSession()
        check(s.begin() != null)
        check(s.begin() == null)
    }
    test("valid completion enters lookup") {
        val s = ScrollLookupSession()
        val ticket = s.begin()!!
        check(s.complete(ticket))
        check(s.state == ScrollLookupSession.State.LOOKUP)
        check(s.begin() == null)
    }
    test("completion cannot be processed twice") {
        val s = ScrollLookupSession()
        val ticket = s.begin()!!
        check(s.complete(ticket))
        check(!s.complete(ticket))
    }
    test("dismiss before capture returns invalidates callback") {
        val s = ScrollLookupSession()
        val ticket = s.begin()!!
        s.reset()
        check(!s.complete(ticket))
    }
    test("old result cannot overwrite a new capture") {
        val s = ScrollLookupSession()
        val old = s.begin()!!
        s.reset()
        val fresh = s.begin()!!
        check(!s.complete(old))
        check(s.complete(fresh))
    }
    test("tap word and dismiss cycle re-arms session") {
        val s = ScrollLookupSession()
        repeat(100) {
            check(s.complete(s.begin()!!))
            s.reset()
        }
        check(s.state == ScrollLookupSession.State.IDLE)
    }
    test("10000 randomized delegated gestures never become lookup") {
        val random = Random(7319)
        repeat(10000) {
            val p = policy()
            p.down(0f, 0f, 0, true)
            check(p.move(random.nextFloat() * 100f + 13f, 0f, 20) == delegate)
            check(p.up(random.nextFloat() * 10f, 0f, 100) == ignore)
        }
    }
    return cases
}
