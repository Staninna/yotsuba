package dev.stan.yotsuba.navigation

import android.content.Intent
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.core.widget.WidgetDeepLink

/** Turns a launch intent (browser link, shared text or a widget tap) into somewhere the app can go. */
object ExternalLinks {
    private val URL = Regex("""https?://\S+""")

    fun fromIntent(intent: Intent?): InternalLink? = fromWidgetExtras(intent) ?: when (intent?.action) {
        Intent.ACTION_VIEW -> fromViewData(intent.dataString)
        Intent.ACTION_SEND -> fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        else -> null
    }

    fun fromViewData(data: String?): InternalLink? = data?.let(Urls::parseInternal)

    /** Shared text is usually a sentence with a URL in it; the first 4chan link wins. */
    fun fromSharedText(text: String?): InternalLink? =
        text?.let { s -> URL.findAll(s).firstNotNullOfOrNull { m -> Urls.parseInternal(m.value.trimEnd('.', ',', ')', '>')) } }

    /** A widget tap carries no action, only the (board, threadNo) extras. */
    private fun fromWidgetExtras(intent: Intent?): InternalLink? {
        val board = intent?.getStringExtra(WidgetDeepLink.EXTRA_BOARD) ?: return null
        val threadNo = intent.getLongExtra(WidgetDeepLink.EXTRA_THREAD_NO, -1L)
        return if (threadNo > 0) InternalLink.Thread(board, threadNo) else null
    }
}
