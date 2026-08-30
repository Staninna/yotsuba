package dev.stan.yotsuba.feature.media

import java.io.File
import java.net.URLEncoder

/**
 * The reverse image search engines the viewer can hand a picture to. Each takes the image
 * as a URL query parameter, so they only work for media that is reachable online; a file
 * that exists only on the phone goes out through the share sheet instead.
 */
enum class ReverseSearchEngine(val label: String, private val prefix: String) {
    GOOGLE_LENS("Google Lens", "https://lens.google.com/uploadbyurl?url="),
    SAUCENAO("SauceNAO", "https://saucenao.com/search.php?url="),
    IQDB("IQDB", "https://iqdb.org/?url="),
    TINEYE("TinEye", "https://tineye.com/search?url="),
    YANDEX("Yandex", "https://yandex.com/images/search?rpt=imageview&url="),
    ;

    /** The page to open for [imageUrl]; the URL is encoded as a query value, `&` and `?` included. */
    fun searchUrl(imageUrl: String): String = prefix + URLEncoder.encode(imageUrl, "UTF-8")
}

/**
 * What one search can work from. [remoteUrl] is the copy the engines can fetch, when
 * there is one; [file] is the local copy the share sheet can send. A thread image has
 * both once it is saved, a vault-only or imported file has only the file, and a video
 * frame is always a file.
 */
data class ReverseSearchTarget(
    val remoteUrl: String?,
    val file: File?,
    /** Extension with its dot, for the share MIME type. */
    val ext: String,
) {
    val canUseEngines: Boolean get() = remoteUrl != null
    val canShare: Boolean get() = file != null
}

/** [url] when an engine could fetch it: `http(s)`, not the `file://` an imported thread carries. */
fun remoteImageUrl(url: String?): String? =
    url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
