package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.DataResult
import java.io.File

/** The engines whose own upload form answers with something that yields a results URL. */
enum class DirectUploadEngine { TINEYE, YANDEX }

/** The third-party hosts a local-only file may be parked on so an engine can fetch it. */
enum class TemporaryHost {
    /** Deleted after one hour. */
    LITTERBOX,
    /** Asked to expire after 24 hours; the host makes no promise about it. */
    ZERO_X_ZERO,
}

/** Where a temporarily hosted file ended up, and on which host, so the UI can say so. */
data class HostedFile(val url: String, val host: TemporaryHost)

/**
 * Puts a local-only file where a reverse image search engine can see it. Two routes: the
 * engine's own upload form, or a temporary host whose URL then goes through the ordinary
 * by-URL search path. Both hand back a URL to open, never a body to render.
 */
interface ReverseSearchRepository {
    /** Uploads [file] to [engine]'s own form and returns the results page URL. */
    suspend fun directSearchUrl(engine: DirectUploadEngine, file: File, ext: String): DataResult<String>

    /** Uploads [file] to [TemporaryHost.LITTERBOX], falling back to [TemporaryHost.ZERO_X_ZERO]. */
    suspend fun hostTemporarily(file: File, ext: String): DataResult<HostedFile>
}
