package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.VaultPaths
import java.io.File

/** One post of the fixture thread, with a stable body and timestamp derived from its number. */
fun vaultPost(no: Long, quotes: List<Long> = emptyList(), isOp: Boolean = false) = VaultPostMeta(
    no = no,
    isOp = isOp,
    subject = if (isOp) "Cats" else null,
    timeSeconds = 1_700_000_000 + no,
    body = PostText(listOf(PostSegment("post $no"))),
    quotedPostNos = quotes,
)

/** OP 1; 2 -> 1; 3 -> 2; 4 stray; 5 -> 4; 6 -> 3. */
fun vaultThread() = listOf(
    vaultPost(1, isOp = true), vaultPost(2, listOf(1)), vaultPost(3, listOf(2)),
    vaultPost(4), vaultPost(5, listOf(4)), vaultPost(6, listOf(3)),
)

/** The decoded meta.json sidecar of a thread directory. */
fun readMeta(dir: File) = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!
