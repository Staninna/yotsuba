package dev.stan.yotsuba.core.log

import dev.stan.yotsuba.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTest {

    private class Recording : Logs.Sink {
        val lines = mutableListOf<String>()
        var lastThrowable: Throwable? = null
        override fun d(tag: String, msg: String) { lines += "d/$tag: $msg" }
        override fun w(tag: String, msg: String, t: Throwable?) {
            lines += "w/$tag: $msg"
            lastThrowable = t
        }
    }

    private val sink = Recording()

    @After
    fun restore() {
        Logs.sink = Logs.Android
    }

    @Test
    fun `w goes through the installed sink with its throwable`() {
        Logs.sink = sink
        val boom = IllegalStateException("boom")
        Log.w("Tag", "it broke", boom)
        assertEquals(listOf("w/Tag: it broke"), sink.lines)
        assertSame(boom, sink.lastThrowable)
    }

    @Test
    fun `d only reaches the sink in debug builds`() {
        Logs.sink = sink
        Log.d("Tag", "detail")
        assertEquals(if (BuildConfig.DEBUG) listOf("d/Tag: detail") else emptyList(), sink.lines)
    }

    @Test
    fun `the android sink never throws off-device`() {
        // Whatever the stubbed android.util.Log does under this JVM, the caller survives.
        Logs.Android.d("Tag", "detail")
        Logs.Android.w("Tag", "warning", null)
        Logs.Android.w("Tag", "warning", RuntimeException("x"))
        assertTrue(true)
    }
}
