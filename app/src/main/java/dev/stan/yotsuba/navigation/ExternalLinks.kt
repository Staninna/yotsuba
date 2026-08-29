package dev.stan.yotsuba.navigation

import android.content.Intent
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.util.Urls.InternalLink

/** Turns a launch intent (browser link or shared text) into somewhere the app can go. */
object ExternalLinks {
    private val URL = Regex("""https?://\S+""")

    fun fromIntent(intent: Intent?): InternalLink? = when (intent?.action) {
        Intent.ACTION_VIEW -> fromViewData(intent.dataString)
        Intent.ACTION_SEND -> fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        else -> null
    }

    fun fromViewData(data: String?): InternalLink? = data?.let(Urls::parseInternal)

    /** Shared text is usually a sentence with a URL in it; the first 4chan link wins. */
    fun fromSharedText(text: String?): InternalLink? =
        text?.let { URL.findAll(it) }
            ?.map { m -> Urls.parseInternal(m.value.trimEnd('.', ',', ')', '>')) }
            ?.firstOrNull { it != null }
}
