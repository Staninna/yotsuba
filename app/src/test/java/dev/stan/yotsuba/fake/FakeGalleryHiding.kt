package dev.stan.yotsuba.fake

import dev.stan.yotsuba.core.vault.GalleryHiding
import java.io.IOException

/** The `.nomedia` marker as a boolean; [access] false plays a vault the app cannot reach. */
class FakeGalleryHiding(var hidden: Boolean = false, var access: Boolean = true) : GalleryHiding {
    var failure: Exception? = null
    var setCalls = 0

    override suspend fun isHidden(): Boolean? = if (access) hidden else null

    override suspend fun setHidden(hidden: Boolean): Result<Boolean> {
        setCalls++
        if (!access) return Result.failure(IOException("no storage access"))
        failure?.let { return Result.failure(it) }
        this.hidden = hidden
        return Result.success(hidden)
    }
}
