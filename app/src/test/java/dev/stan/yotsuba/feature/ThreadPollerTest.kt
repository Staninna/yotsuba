package dev.stan.yotsuba.feature

import dev.stan.yotsuba.feature.thread.ThreadPoller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadPollerTest {

    @Test fun `polls with growing backoff and clamps at the longest interval`() = runTest {
        var polls = 0
        val poller = ThreadPoller(isEnabled = { true }, poll = { polls++ })
        poller.start(backgroundScope)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, polls)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, polls)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(3, polls)
        advanceTimeBy(300_000); runCurrent()
        assertEquals(4, polls)
        advanceTimeBy(300_000); runCurrent()
        assertEquals(5, polls) // stays at the longest interval
        poller.stop()
    }

    @Test fun `idles while disabled and resumes once enabled`() = runTest {
        var polls = 0
        var enabled = false
        val poller = ThreadPoller(isEnabled = { enabled }, poll = { polls++ })
        poller.start(backgroundScope)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, polls)
        enabled = true
        // Next 5s idle re-check sees enabled, then the first interval elapses.
        advanceTimeBy(5_000 + 10_000); runCurrent()
        assertEquals(1, polls)
    }

    @Test fun `resetBackoff during a poll returns to the shortest interval`() = runTest {
        // Mirrors the VM: new posts found while polling reset the backoff for the next wait.
        var polls = 0
        var poller: ThreadPoller? = null
        poller = ThreadPoller(
            isEnabled = { true },
            poll = { polls++; if (polls == 2) poller?.resetBackoff() },
        )
        poller.start(backgroundScope)
        advanceTimeBy(10_000 + 30_000); runCurrent()
        assertEquals(2, polls)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(3, polls)
    }

    @Test fun `stop halts polling and start is idempotent`() = runTest {
        var polls = 0
        val poller = ThreadPoller(isEnabled = { true }, poll = { polls++ })
        poller.start(backgroundScope)
        poller.start(backgroundScope) // no second loop
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, polls)
        poller.stop()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, polls)
    }
}
