package dev.stan.yotsuba.core.log

import dev.stan.yotsuba.BuildConfig

/**
 * The app's one logging entry point. Every boundary that swallows a failure (a catch that
 * maps to a UI state, a worker that gives up, a best-effort cleanup) writes one line here,
 * so a silent failure is at least visible in logcat.
 *
 * [d] is a no-op in release builds. [w] always logs. Both go through [Logs.sink], which the
 * JVM tests replace: android.util.Log is a stub off-device and what it does depends on the
 * Gradle test options, so nothing here may touch it directly.
 */
object Log {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Logs.sink.d(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Logs.sink.w(tag, msg, t)
    }
}

/** Where [Log] lines go. Tests swap [sink]; production leaves the Android default. */
object Logs {
    interface Sink {
        fun d(tag: String, msg: String)
        fun w(tag: String, msg: String, t: Throwable?)
    }

    /** Forwards to android.util.Log, swallowing anything a stubbed runtime throws. */
    object Android : Sink {
        override fun d(tag: String, msg: String) {
            try {
                android.util.Log.d(tag, msg)
            } catch (_: RuntimeException) {
                // Unit-test stub ("Method not mocked"); logging must never crash the caller.
            }
        }

        override fun w(tag: String, msg: String, t: Throwable?) {
            try {
                if (t == null) android.util.Log.w(tag, msg) else android.util.Log.w(tag, msg, t)
            } catch (_: RuntimeException) {
                // As above.
            }
        }
    }

    @Volatile
    var sink: Sink = Android
}
