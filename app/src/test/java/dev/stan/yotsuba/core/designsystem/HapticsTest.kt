package dev.stan.yotsuba.core.designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticsTest {

    @Test
    fun `rememberHaptics composes and every gesture runs`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var haptics: Haptics? = null
        activity.setContent {
            val h = rememberHaptics()
            SideEffect { haptics = h }
        }
        Robolectric.flushForegroundThreadScheduler()
        val h = haptics
        assertNotNull(h)
        h!!.longPress(); h.confirm(); h.reject(); h.tick()
    }
}
