package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup

/** Content hashes over the vault: recorded on save, backfilled on demand, queried for duplicates. */
interface VaultDedupRepository {
    /** Path of a saved file with this MD5 (base64 of the raw digest), or null. */
    suspend fun findByMd5(md5: String): String?

    /** Attaches the MD5 the source reported to a row that was just saved. */
    suspend fun recordMd5(url: String, md5: String)

    /** How many rows still lack a hash; zero means [backfillHashes] has nothing to do. */
    suspend fun missingHashCount(): Int

    /**
     * Hashes every row that lacks one: MD5 for everything, dHash and pixel size for images.
     * Rows already done are skipped, so it can be interrupted and resumed. [onProgress]
     * reports `(done, total)`.
     */
    suspend fun backfillHashes(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> })

    /** Groups of duplicates by [mode]; [maxDistance] only matters for [DedupMode.SIMILAR]. */
    suspend fun findDuplicates(mode: DedupMode, maxDistance: Int = DEFAULT_MAX_DISTANCE): List<DuplicateGroup>

    companion object {
        const val DEFAULT_MAX_DISTANCE = 6
    }
}
