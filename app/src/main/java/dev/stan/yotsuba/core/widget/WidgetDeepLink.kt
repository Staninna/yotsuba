package dev.stan.yotsuba.core.widget

/**
 * Intent extras a widget row tap puts on the launch intent. The Glance action keys are
 * built from these, and `ExternalLinks.fromIntent` turns them back into a thread link.
 */
object WidgetDeepLink {
    const val EXTRA_BOARD = "dev.stan.yotsuba.widget.BOARD"
    const val EXTRA_THREAD_NO = "dev.stan.yotsuba.widget.THREAD_NO"
}
