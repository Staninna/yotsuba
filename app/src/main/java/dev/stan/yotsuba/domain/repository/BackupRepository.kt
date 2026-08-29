package dev.stan.yotsuba.domain.repository

/** Outcome of one export or import pass. */
sealed interface BackupResult {
    /** Written to the vault root at [exportedAt] (epoch millis). */
    data class Exported(val exportedAt: Long) : BackupResult

    data class Imported(val bookmarks: Int, val hiddenThreads: Int) : BackupResult

    /** The vault is not readable or writable: nothing was touched. */
    data object NoAccess : BackupResult

    /** Import asked for, no file at the vault root. */
    data object NoBackup : BackupResult

    data class Failed(val message: String) : BackupResult
}

/** A backup file found at the vault root, before anything has been read out of it. */
data class BackupInfo(val exportedAt: Long)

/**
 * Bookmarks, hidden threads and settings die with an uninstall; the vault on shared storage
 * does not. This copies them into a JSON file next to the saved media so a reinstall can pull
 * them back.
 */
interface BackupRepository {
    suspend fun export(): BackupResult

    /**
     * Bookmarks merge by (board, threadNo) keeping the higher read mark; hidden threads union;
     * settings are replaced outright.
     */
    suspend fun import(): BackupResult

    /** The backup at the vault root, or null when there is none (or it cannot be read). */
    suspend fun available(): BackupInfo?

    /** No bookmarks and no settings ever written: the install has nothing to lose to a restore. */
    suspend fun isFreshInstall(): Boolean

    /** For callers built without a vault, such as tests: never exports, never finds a file. */
    object None : BackupRepository {
        override suspend fun export(): BackupResult = BackupResult.NoAccess
        override suspend fun import(): BackupResult = BackupResult.NoBackup
        override suspend fun available(): BackupInfo? = null
        override suspend fun isFreshInstall(): Boolean = false
    }
}
