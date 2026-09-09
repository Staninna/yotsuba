package dev.stan.yotsuba.core.network

import dev.stan.yotsuba.domain.model.ArchiveSource

/**
 * Which third-party archive keeps which board, and the URLs to reach it. The one table
 * the app consults; nothing else may hard-code an archive host.
 *
 * A board listed under more than one archive is tried at each in [ArchiveSource] order,
 * so /vr/ asks desuarchive before archived.moe. The board lists were checked against each
 * host's `/_/api/chan/thread/` on 2026-09-09 (a carried board answers "Thread not found",
 * an unknown one 422) except archived.moe, which sits behind a Cloudflare challenge that
 * curl cannot pass; its list is 4chan X's `archives.json`. It stays last in the chain so
 * a challenge page costs one wasted request after every specialist has said no.
 *
 * Warosu runs Fuuka, not FoolFuuka, and exposes no JSON API: reading it means scraping
 * HTML. That is the hook: flip [Archive.foolFuuka] (or give it its own endpoint) and
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
                "a", "aco", "an", "c", "cgl", "co", "d", "fit", "g", "his", "int", "k", "m", "mlp",
                "mu", "q", "qa", "r9k", "tg", "trash", "vr", "wsg",
            ),
        ),
        // arch.b4k.co now 302s every request here.
        ArchiveSource.B4K to Archive(
            webBase = "https://arch.b4k.dev",
            foolFuuka = true,
            boards = setOf("g", "mlp", "qb", "v", "vg", "vm", "vmg", "vp", "vrpg", "vst"),
        ),
        ArchiveSource.FOUR_PLEBS to Archive(
            webBase = "https://archive.4plebs.org",
            foolFuuka = true,
            boards = setOf("adv", "f", "hr", "mlpol", "mo", "o", "pol", "s4s", "sp", "tg", "trv", "tv", "x"),
        ),
        ArchiveSource.FIREDEN to Archive(
            webBase = "https://boards.fireden.net",
            foolFuuka = true,
            boards = setOf("cm", "sci", "y"),
        ),
        ArchiveSource.PALANQ to Archive(
            webBase = "https://archive.palanq.win",
            foolFuuka = true,
            boards = setOf(
                "bant", "c", "con", "e", "i", "n", "news", "out", "p", "pw", "qst", "toy", "vip", "vp",
                "vt", "w", "wg", "wsr",
            ),
        ),
        ArchiveSource.ARCHIVED_MOE to Archive(
            webBase = "https://archived.moe",
            foolFuuka = true,
            boards = setOf(
                "3", "a", "aco", "adv", "an", "asp", "b", "bant", "biz", "c", "can", "cgl", "ck", "cm", "co",
                "cock", "con", "d", "diy", "e", "f", "fa", "fap", "fit", "fitlit", "g", "gd", "gif", "h", "hc",
                "his", "hm", "hr", "i", "ic", "int", "jp", "k", "lgbt", "lit", "m", "mlp", "mlpol", "mo",
                "mtv", "mu", "n", "news", "o", "out", "outsoc", "p", "po", "pol", "pw", "q", "qa", "qb", "qst",
                "r", "r9k", "s", "s4s", "sci", "soc", "sp", "spa", "t", "tg", "toy", "trash", "trv", "tv",
                "u", "v", "vg", "vint", "vip", "vm", "vmg", "vp", "vr", "vrpg", "vst", "vt", "w", "wg", "wsg",
                "wsr", "x", "xs", "y",
            ),
        ),
        ArchiveSource.WAROSU to Archive(
            webBase = "https://warosu.org",
            foolFuuka = false,
            boards = setOf("3", "biz", "cgl", "ck", "diy", "fa", "ic", "jp", "lit", "sci", "vr", "vt"),
        ),
    )

    /** Every archive that carries [board], in the order to try them; empty when none does. */
    fun sourcesFor(board: String): List<ArchiveSource> =
        ArchiveSource.entries.filter { board in archives.getValue(it).boards }

    /** The thread as a person would open it in a browser. */
    fun threadUrl(source: ArchiveSource, board: String, no: Long): String =
        "${archives.getValue(source).webBase}/$board/thread/$no"

    /** The FoolFuuka JSON endpoint for a thread, or null for an archive without one. */
    fun apiUrl(source: ArchiveSource, board: String, no: Long): String? =
        archives.getValue(source).takeIf { it.foolFuuka }?.let { "${it.webBase}/_/api/chan/thread/?board=$board&num=$no" }
}
