package dev.stan.yotsuba.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {

    @Test
    fun `reduced motion follows the animator duration scale`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertFalse(isReducedMotion(context))
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(isReducedMotion(context))
    }

    @Test
    fun `rememberReducedMotion is true with the setting on and animator scale 1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var withSetting: Boolean? = null
        var withoutSetting: Boolean? = null
        activity.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                val v = rememberReducedMotion()
                SideEffect { withSetting = v }
            }
            val v = rememberReducedMotion()
            SideEffect { withoutSetting = v }
        }
        Robolectric.flushForegroundThreadScheduler()
        assertTrue(withSetting == true)
        assertFalse(withoutSetting == true)
    }
}
