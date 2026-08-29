package dev.stan.yotsuba.core.network

import dev.stan.yotsuba.domain.model.ArchiveSource

/**
 * Which third-party archive keeps which board, and the URLs to reach it. The one table
 * the app consults; nothing else may hard-code an archive host.
 *
 * A board listed under more than one archive resolves to the first hit in [ArchiveSource]
 * order, so /vr/ goes to desuarchive even though warosu carries it too.
 */
object ArchiveHosts {

    private val boards: Map<ArchiveSource, Set<String>> = mapOf(
        ArchiveSource.DESU to setOf(
            "a", "aco", "an", "c", "cgl", "co", "d", "fit", "his", "int", "k", "m", "mlp",
            "mu", "q", "qa", "r9k", "tg", "trv", "vr", "wsg",
        ),
        ArchiveSource.B4K to setOf("v", "vg", "vm", "vmg", "vp", "vrpg", "vst"),
        ArchiveSource.WAROSU to setOf(
            "3", "biz", "ck", "diy", "fa", "g", "ic", "jp", "lit", "sci", "vr", "w", "wg",
        ),
    )

    private val webBase: Map<ArchiveSource, String> = mapOf(
        ArchiveSource.DESU to "https://desuarchive.org",
        ArchiveSource.B4K to "https://arch.b4k.co",
        ArchiveSource.WAROSU to "https://warosu.org",
    )

    /** The archive that carries [board], or null when none of the known ones does. */
    fun sourceFor(board: String): ArchiveSource? =
        ArchiveSource.entries.firstOrNull { board in boards.getValue(it) }

    /** The thread as a person would open it in a browser. */
    fun threadUrl(source: ArchiveSource, board: String, no: Long): String =
        "${webBase.getValue(source)}/$board/thread/$no"

    /**
     * The FoolFuuka JSON endpoint for a thread, or null for an archive without one.
     *
     * Warosu runs Fuuka, not FoolFuuka, and exposes no JSON API: reading it means
     * scraping HTML. That is the hook -- return a URL here and teach
     * [dev.stan.yotsuba.core.network.dto.FoolFuukaThreadDto] (or a sibling) its shape.
     */
    fun apiUrl(source: ArchiveSource, board: String, no: Long): String? = when (source) {
        ArchiveSource.DESU, ArchiveSource.B4K ->
            "${webBase.getValue(source)}/_/api/chan/thread/?board=$board&num=$no"
        ArchiveSource.WAROSU -> null
    }
}
