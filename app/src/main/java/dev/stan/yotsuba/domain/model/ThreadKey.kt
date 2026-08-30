package dev.stan.yotsuba.domain.model

/**
 * The stable identity of a thread across boards, `"<board>/<threadNo>"`. Use it wherever a
 * lazy list, cache or map needs one key per thread; thread numbers alone repeat between
 * boards.
 */
fun threadKey(board: String, threadNo: Long): String = "$board/$threadNo"

val VaultLocation.key: String get() = threadKey(board, threadNo)
