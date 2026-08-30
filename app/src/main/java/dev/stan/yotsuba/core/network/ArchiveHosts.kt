package dev.stan.yotsuba.core.network

import dev.stan.yotsuba.domain.model.ArchiveSource

/**
 * Which third-party archive keeps which board, and the URLs to reach it. The one table
 * the app consults; nothing else may hard-code an archive host.
 *
 * A board listed under more than one archive resolves to the first hit in [ArchiveSource]
 * order, so /vr/ goes to desuarchive even though warosu carries it too.
 *
 * Warosu runs Fuuka, not FoolFuuka, and exposes no JSON API: reading it means scraping
 * HTML. That is the hook -- flip [Archive.foolFuuka] (or give it its own endpoint) and
 * teach [dev.stan.yotsuba.core.network.dto.FoolFuukaThreadDto] (or a sibling) its shape.
 */
object ArchiveHosts {

    /** Everything known about one archive, so adding one is a single entry below. */
    private class Archive(val webBase: String, val foolFuuka: Boolean, val boards: Set<String>)

    private val archives: Map<ArchiveSource, Archive> = mapOf(
        ArchiveSource.DESU to Archive(
            webBase = "https://desuarchive.org",
            foolFuuka = true,
            boards = setOf(
                "a", "aco", "an", "c", "cgl", "co", "d", "fit", "his", "int", "k", "m", "mlp",
                "mu", "q", "qa", "r9k", "tg", "trv", "vr", "wsg",
            ),
        ),
        ArchiveSource.B4K to Archive(
            webBase = "https://arch.b4k.co",
            foolFuuka = true,
            boards = setOf("v", "vg", "vm", "vmg", "vp", "vrpg", "vst"),
        ),
        ArchiveSource.WAROSU to Archive(
            webBase = "https://warosu.org",
            foolFuuka = false,
            boards = setOf("3", "biz", "ck", "diy", "fa", "g", "ic", "jp", "lit", "sci", "vr", "w", "wg"),
        ),
    )

    /** The archive that carries [board], or null when none of the known ones does. */
    fun sourceFor(board: String): ArchiveSource? =
        ArchiveSource.entries.firstOrNull { board in archives[it]?.boards.orEmpty() }

    /** The thread as a person would open it in a browser. */
    fun threadUrl(source: ArchiveSource, board: String, no: Long): String =
        "${archives.getValue(source).webBase}/$board/thread/$no"

    /** The FoolFuuka JSON endpoint for a thread, or null for an archive without one. */
    fun apiUrl(source: ArchiveSource, board: String, no: Long): String? =
        archives.getValue(source).takeIf { it.foolFuuka }?.let { "${it.webBase}/_/api/chan/thread/?board=$board&num=$no" }
}
