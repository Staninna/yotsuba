package dev.stan.yotsuba.domain.model

/** A thread the user hid from a board's catalog. */
data class HiddenThread(
    val board: String,
    val threadNo: Long,
)
