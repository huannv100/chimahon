package eu.kanade.tachiyomi.ui.dictionary

/** Pure gesture policy. A delegated gesture can never subsequently become a lookup tap. */
internal class ScrollLookupTapPolicy(
    private val touchSlop: Float,
    private val holdTimeoutMillis: Long,
) {
    enum class Decision { WAIT, DELEGATE, LOOKUP, IGNORE }

    private var tracking = false
    private var startX = 0f
    private var startY = 0f
    private var startedAt = 0L

    init {
        require(touchSlop.isFinite() && touchSlop > 0)
        require(holdTimeoutMillis > 0)
    }

    fun down(x: Float, y: Float, time: Long, eligible: Boolean): Decision {
        tracking = eligible && x.isFinite() && y.isFinite()
        startX = x
        startY = y
        startedAt = time
        return if (tracking) Decision.WAIT else Decision.DELEGATE
    }

    fun move(x: Float, y: Float, time: Long, pointerCount: Int = 1): Decision {
        if (!tracking) return Decision.IGNORE
        val dx = x - startX
        val dy = y - startY
        if (pointerCount != 1 || !x.isFinite() || !y.isFinite() ||
            time < startedAt || time - startedAt >= holdTimeoutMillis ||
            dx * dx + dy * dy > touchSlop * touchSlop
        ) {
            tracking = false
            return Decision.DELEGATE
        }
        return Decision.WAIT
    }

    fun up(x: Float, y: Float, time: Long): Decision {
        val movement = move(x, y, time)
        tracking = false
        return if (movement == Decision.WAIT) Decision.LOOKUP else movement
    }

    fun timeout(): Decision {
        if (!tracking) return Decision.IGNORE
        tracking = false
        return Decision.DELEGATE
    }

    fun cancel() {
        tracking = false
    }
}

/** Invalidates late screenshot callbacks after dismissal, rotation, stop or another request. */
internal class ScrollLookupSession {
    enum class State { IDLE, CAPTURING, LOOKUP }

    var state: State = State.IDLE
        private set
    private var generation = 0L

    fun begin(): Long? {
        if (state != State.IDLE) return null
        state = State.CAPTURING
        return ++generation
    }

    fun isPending(ticket: Long): Boolean = state == State.CAPTURING && ticket == generation

    fun complete(ticket: Long): Boolean {
        if (!isPending(ticket)) return false
        state = State.LOOKUP
        return true
    }

    fun reset() {
        ++generation
        state = State.IDLE
    }
}
