package dev.stan.yotsuba.domain.model

/**
 * What a pass over the saved threads managed to refresh.
 *
 * [gone] threads are the point of the exercise rather than a failure: once a thread 404s
 * its comment section is unreachable forever, so whatever was captured before is all
 * there will ever be.
 */
data class VaultSyncSummary(
    val updated: Int = 0,
    val gone: Int = 0,
    val failed: Int = 0,
    /** Dead threads whose sidecar was compacted to the saved conversations during this pass. */
    val pruned: Int = 0,
    /** True when the API asked us to back off and the pass stopped short. */
    val rateLimited: Boolean = false,
) {
    val checked: Int get() = updated + gone + failed
}
