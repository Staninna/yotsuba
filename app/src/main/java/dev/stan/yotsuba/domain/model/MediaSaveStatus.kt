package dev.stan.yotsuba.domain.model

/**
 * Where a post's media stands with respect to the vault: on disk, waiting, in flight, or
 * failed with why. Absent from a status map means never asked for.
 */
sealed interface MediaSaveStatus {
    data object Saved : MediaSaveStatus
    data object Queued : MediaSaveStatus
    data object Downloading : MediaSaveStatus
    data class Failed(val error: VaultError) : MediaSaveStatus

    /** Not downloaded: a byte-identical file is already in the vault at [existingPath]. */
    data class AlreadySaved(val existingPath: String) : MediaSaveStatus

    /** Queued or downloading: the save is still going to happen. */
    val inProgress: Boolean get() = this is Queued || this is Downloading
}
