package dev.stan.yotsuba.feature.thread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Auto-refresh loop with backoff (D17). Runs only between [start] and [stop], both driven by the
 * screen's visibility, and re-checks [isEnabled] every [idleRecheckMs] while disabled.
 */
class ThreadPoller(
    private val isEnabled: suspend () -> Boolean,
    private val poll: suspend () -> Unit,
    private val intervalsMs: List<Long> = listOf(10_000L, 30_000L, 60_000L, 300_000L),
    private val idleRecheckMs: Long = 5_000L,
) {
    private var job: Job? = null
    private var backoffIndex = 0

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                if (!isEnabled()) {
                    delay(idleRecheckMs)
                    continue
                }
                delay(intervalsMs[backoffIndex])
                backoffIndex = (backoffIndex + 1).coerceAtMost(intervalsMs.size - 1)
                poll()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** New posts arrived (or the user re-enabled refresh): poll eagerly again. */
    fun resetBackoff() {
        backoffIndex = 0
    }
}
