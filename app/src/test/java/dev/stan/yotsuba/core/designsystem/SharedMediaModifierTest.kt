package dev.stan.yotsuba.core.designsystem

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import dev.stan.yotsuba.core.designsystem.component.sharedMedia
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * `Modifier.sharedMedia` must be a plain identity outside a `SharedTransitionLayout`, so
 * previews and screen tests can compose thumbnails and viewer pages without the shell.
 */
@RunWith(RobolectricTestRunner::class)
class SharedMediaModifierTest {

    @Test
    fun `composes as identity when no scope is provided`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var result: Modifier? = null
        val input = Modifier
        activity.setContent {
            val out = input.sharedMedia("https://i.4cdn.org/g/1.png")
            SideEffect { result = out }
            Box(out)
        }
        Robolectric.flushForegroundThreadScheduler()
        activity.window.decorView.measure(0, 0)

        assertTrue("composition ran", result != null)
        assertSame(input, result)
    }

    @Test
    fun `explicit-scope form is identity when either scope is null`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var result: Modifier? = null
        val input = Modifier
        activity.setContent {
            val out = input.sharedMedia("key", shared = null, visibility = null)
            SideEffect { result = out }
        }
        Robolectric.flushForegroundThreadScheduler()
        assertSame(input, result)
        (activity as Activity).finish()
    }
}
