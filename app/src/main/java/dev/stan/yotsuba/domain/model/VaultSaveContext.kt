package dev.stan.yotsuba.domain.model

/** Everything known about a post at save time, used to file media into the vault. */
data class VaultSaveContext(
    val board: String,
    val threadNo: Long,
    /** Thread (OP) subject, for the thread directory slug. */
    val threadSubject: String?,
    /** Plain-text OP excerpt, slug fallback when the thread has no subject. */
    val opExcerpt: String?,
    val post: ThreadPost?,
)
