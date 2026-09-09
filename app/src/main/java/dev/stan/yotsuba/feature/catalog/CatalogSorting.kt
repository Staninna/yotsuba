package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogSort
import dev.stan.yotsuba.domain.model.CatalogThread

/**
 * The catalog in [sort] order. Bump order is the list as the API sent it; every other sort
 * is descending and stable, so ties keep their bump order.
 */
fun List<CatalogThread>.sortedBy(sort: CatalogSort, nowSeconds: Long = System.currentTimeMillis() / 1000): List<CatalogThread> =
    when (sort) {
        CatalogSort.BUMP_ORDER -> this
        CatalogSort.CREATION_TIME -> sortedByDescending { it.createdAt }
        CatalogSort.REPLY_COUNT -> sortedByDescending { it.replyCount }
        CatalogSort.IMAGE_COUNT -> sortedByDescending { it.imageCount }
        CatalogSort.REPLIES_PER_HOUR -> sortedByDescending { it.repliesPerHour(nowSeconds) }
    }

/** Replies over the thread's age, with the age floored at one hour so a fresh thread does not divide by nothing. */
fun CatalogThread.repliesPerHour(nowSeconds: Long): Double =
    replyCount / ((nowSeconds - createdAt) / 3600.0).coerceAtLeast(1.0)
