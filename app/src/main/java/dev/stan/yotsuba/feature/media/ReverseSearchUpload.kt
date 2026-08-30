package dev.stan.yotsuba.feature.media

import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.domain.repository.TemporaryHost

/**
 * The engine's own upload form, when it answers with something that yields a shareable
 * results URL. The others give back an HTML results page with nothing to hand the
 * browser, so the temporary host is their only route.
 */
val ReverseSearchEngine.directUpload: DirectUploadEngine?
    get() = when (this) {
        ReverseSearchEngine.TINEYE -> DirectUploadEngine.TINEYE
        ReverseSearchEngine.YANDEX -> DirectUploadEngine.YANDEX
        else -> null
    }

val ReverseSearchEngine.hasDirectUpload: Boolean get() = directUpload != null

/** Where one local search stands; the sheet renders it. */
sealed interface LocalSearchState {
    data object Idle : LocalSearchState
    /**
     * Nothing uploads until the user confirms. [direct] when the file would go to [engine]'s
     * own form; otherwise it goes to the temporary host and only its URL reaches the engine.
     */
    data class ConfirmUpload(val engine: ReverseSearchEngine, val direct: Boolean) : LocalSearchState
    data class Uploading(val engine: ReverseSearchEngine) : LocalSearchState
    /** [url] is the results page; the screen opens it and resets to [Idle]. */
    data class Opened(val engine: ReverseSearchEngine, val url: String) : LocalSearchState
    /** [canFallback] when the temporary host is still worth offering. */
    data class Failed(val engine: ReverseSearchEngine, val canFallback: Boolean) : LocalSearchState
}
