package dev.stan.yotsuba.core.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The thread a widget tap asked for. MainActivity reads the intent extras and parks them
 * here; navigation collects [pending] and calls [clear] once it has navigated. Kept as a
 * process-wide holder so the activity hook stays a single line and the nav layer can
 * consume it whichever way it ends up being wired.
 */
object WidgetDeepLink {
    const val EXTRA_BOARD = "dev.stan.yotsuba.widget.BOARD"
    const val EXTRA_THREAD_NO = "dev.stan.yotsuba.widget.THREAD_NO"

    data class Target(val board: String, val threadNo: Long)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    /** Parses a launch intent; a no-op for intents that did not come from the widget. */
    fun consume(intent: Intent?) {
        val board = intent?.getStringExtra(EXTRA_BOARD) ?: return
        val threadNo = intent.getLongExtra(EXTRA_THREAD_NO, -1L)
        if (threadNo <= 0) return
        _pending.value = Target(board, threadNo)
        // Strip so a configuration change does not replay the same tap.
        intent.removeExtra(EXTRA_BOARD)
        intent.removeExtra(EXTRA_THREAD_NO)
    }

    fun clear() {
        _pending.value = null
    }
}
