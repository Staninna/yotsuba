package dev.stan.yotsuba.feature.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** The loop handles: inside the video, in order, never closer than the minimum gap. */
class LoopRangeTest {

    @Test fun `handles inside the video and far enough apart are left alone`() {
        assertEquals(LoopRange(1_000, 4_000), loopRange(1_000, 4_000, 10_000))
    }

    @Test fun `handles are pulled inside the video`() {
        assertEquals(LoopRange(0, 10_000), loopRange(-500, 12_000, 10_000))
    }

    @Test fun `the end handle gives way to keep the minimum gap`() {
        assertEquals(LoopRange(3_000, 3_000 + LOOP_MIN_GAP_MS), loopRange(3_000, 3_100, 10_000))
        assertEquals(LoopRange(3_000, 3_000 + LOOP_MIN_GAP_MS), loopRange(3_000, 2_000, 10_000))
    }

    @Test fun `a start handle at the very end leaves room for the gap`() {
        assertEquals(LoopRange(10_000 - LOOP_MIN_GAP_MS, 10_000), loopRange(10_000, 10_000, 10_000))
    }

    @Test fun `a clip shorter than the gap loops whole`() {
        assertEquals(LoopRange(0, 200), loopRange(50, 100, 200))
    }
}
