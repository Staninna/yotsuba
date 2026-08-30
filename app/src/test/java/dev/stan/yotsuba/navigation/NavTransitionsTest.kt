package dev.stan.yotsuba.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import dev.stan.yotsuba.core.designsystem.NavTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavTransitionsTest {
    @Test
    fun `a thread swipe collapses under reduced motion`() {
        val t = NavTransitions(short = 100, medium = 200, reduced = true)
        assertEquals(EnterTransition.None, t.swipeEnter(forward = true))
        assertEquals(EnterTransition.None, t.swipeEnter(forward = false))
        assertEquals(ExitTransition.None, t.swipeExit(forward = true))
        assertEquals(ExitTransition.None, t.swipeExit(forward = false))
    }

    @Test
    fun `a thread swipe slides when motion is on`() {
        val t = NavTransitions(short = 100, medium = 200, reduced = false)
        assertNotEquals(EnterTransition.None, t.swipeEnter(forward = true))
        assertNotEquals(ExitTransition.None, t.swipeExit(forward = false))
    }
}
