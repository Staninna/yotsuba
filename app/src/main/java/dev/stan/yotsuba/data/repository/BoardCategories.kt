package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.domain.model.BoardCategory

/**
 * `boards.json` has no category field (verified), so the app ships 4chan's real category map
 * (D13). Any board absent from this map falls into OTHER, so new boards never break the screen.
 */
object BoardCategories {
    private val map: Map<String, BoardCategory> = buildMap {
        listOf("a", "c", "w", "m", "cgl", "cm", "n", "jp", "vt").forEach { put(it, BoardCategory.JAPANESE_CULTURE) }
        listOf("v", "vg", "vm", "vmg", "vp", "vr", "vrpg", "vst").forEach { put(it, BoardCategory.VIDEO_GAMES) }
        listOf("co", "g", "tv", "k", "o", "an", "tg", "sp", "xs", "pw", "sci", "his", "int", "out", "toy").forEach { put(it, BoardCategory.INTERESTS) }
        listOf("i", "po", "p", "ck", "ic", "wg", "lit", "mu", "fa", "3", "gd", "diy", "wsg", "qst").forEach { put(it, BoardCategory.CREATIVE) }
        listOf("biz", "trv", "fit", "x", "adv", "lgbt", "mlp", "news", "wsr", "vip").forEach { put(it, BoardCategory.OTHER) }
        listOf("b", "r9k", "pol", "bant", "soc", "s4s").forEach { put(it, BoardCategory.MISC) }
        listOf("s", "hc", "hm", "h", "e", "u", "d", "y", "t", "hr", "gif", "aco", "r").forEach { put(it, BoardCategory.ADULT) }
    }

    fun categoryOf(board: String): BoardCategory = map[board] ?: BoardCategory.OTHER
}
