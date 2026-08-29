package dev.stan.yotsuba.feature

import dev.stan.yotsuba.feature.media.ViewerBehaviour
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerBehaviourTest {

    private val tenSeconds = ViewerBehaviour(doubleTapSeek = true, seekStepSeconds = 10)

    @Test fun `a long video gets the configured step verbatim`() {
        assertEquals(10_000L, tenSeconds.seekStepMillis(180_000))
        assertEquals(10_000L, tenSeconds.seekStepMillis(40_000))
    }

    @Test fun `a short video scales the jump down instead of overshooting the end`() {
        assertEquals(7_500L, tenSeconds.seekStepMillis(30_000))
        assertEquals(1_250L, tenSeconds.seekStepMillis(5_000))
        assertEquals(500L, tenSeconds.seekStepMillis(2_000))
    }

    @Test fun `the jump never shrinks below the point of being perceptible`() {
        assertEquals(250L, tenSeconds.seekStepMillis(400))
        assertEquals(250L, tenSeconds.seekStepMillis(1))
    }

    @Test fun `an unknown duration falls back to the configured step`() {
        assertEquals(10_000L, tenSeconds.seekStepMillis(0))
        assertEquals(10_000L, tenSeconds.seekStepMillis(-1))
    }

    @Test fun `the cap binds only while it is smaller than the configured step`() {
        // Below 40 s a quarter of the clip is the smaller number, so the cap decides and
        // four taps cross the video. Above it the configured step wins and a long video
        // takes as many taps as it takes -- which is the point of configuring one.
        listOf(1_000L, 2_500L, 9_000L, 30_000L).forEach { duration ->
            val taps = duration / tenSeconds.seekStepMillis(duration)
            assertEquals("crossing a ${duration}ms clip", true, taps <= 4)
        }
        assertEquals(10_000L, tenSeconds.seekStepMillis(200_000))
    }
}
