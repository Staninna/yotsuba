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
    /**
     * The conversation to preserve as text beside the file: the saved post's transitive
     * parents and replies. Empty when the user has the setting off.
     */
    val conversation: List<ThreadPost> = emptyList(),
) {
    companion object {
        /**
         * How a save is filed: the OP names the thread directory, its plain text stands in
         * for a missing subject, and [includeConversation] keeps the posts around [post].
         * [details] may be null when the thread never loaded; the file is then filed by
         * board and number alone.
         */
        fun of(
            board: String,
            threadNo: Long,
            details: ThreadDetails?,
            post: ThreadPost?,
            includeConversation: Boolean,
        ): VaultSaveContext {
            val op = details?.posts?.firstOrNull { it.isOp }
            return VaultSaveContext(
                board = board,
                threadNo = threadNo,
                threadSubject = op?.subject,
                opExcerpt = op?.body?.plainText?.takeIf { it.isNotBlank() },
                post = post,
                conversation = if (details != null && post != null && includeConversation) {
                    PostGraph.of(details).conversationAround(post.no)
                } else {
                    emptyList()
                },
            )
        }
    }
}
