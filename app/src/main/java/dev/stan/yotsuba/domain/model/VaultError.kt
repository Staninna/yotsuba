package dev.stan.yotsuba.domain.model

/** Why a vault write (save/delete) failed; null result means success. */
sealed interface VaultError {
    /** "All files access" hasn't been granted. */
    data object NoAccess : VaultError

    /** Nothing saved under that URL. */
    data object NotFound : VaultError

    /** Disk or network failure while streaming/moving the file. */
    data class Io(val message: String?) : VaultError
}
