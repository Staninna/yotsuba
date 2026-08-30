package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long a grid delete can be undone before the trash is emptied. */
const val UNDO_WINDOW_MS = 30_000L

/** Entries queued for deletion and whether the grid's undo window applies to them. */
data class VaultDeleteRequest(val entries: List<VaultEntry>, val undoable: Boolean) {
    val single: VaultEntry? get() = entries.singleOrNull()
}

/** What the screen shows for an in-flight delete: its dialog, and its undo snackbar. */
data class VaultDeleteState(
    /** The delete whose confirmation is on screen; here so rotation keeps the dialog. */
    val deleting: VaultDeleteRequest? = null,
    /** Entries sitting in the trash behind an Undo snackbar; null once the window closes. */
    val undo: List<VaultEntry>? = null,
)

/**
 * The vault's delete flow, from request to undo: the confirmation dialog, the trash with
 * its undo window, and the final delete. Outcomes go out through [notify].
 */
class VaultDeletes(
    private val vault: MediaVaultRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val notify: (VaultNotice) -> Unit,
) {
    private val deleting = MutableStateFlow<VaultDeleteRequest?>(null)
    private val undo = MutableStateFlow<List<VaultEntry>?>(null)
    private var undoWindow: Job? = null

    val state: Flow<VaultDeleteState> = combine(deleting, undo) { d, u -> VaultDeleteState(d, u) }

    /** The confirmation setting, kept current so [request] can answer at once. */
    private val confirm: StateFlow<Boolean> = settings.settings
        .map { it.confirmVaultDelete }
        .stateIn(scope, SharingStarted.Eagerly, Settings().confirmVaultDelete)

    /**
     * Queues [entries] for deletion. With the confirmation setting on, the dialog shows
     * first; off, the delete runs at once. [undoable] deletes go through the trash and stay
     * recoverable for [UNDO_WINDOW_MS]; the viewer's delete is final, the grid's is not.
     */
    fun request(entries: List<VaultEntry>, undoable: Boolean) {
        if (entries.isEmpty()) return
        val request = VaultDeleteRequest(entries, undoable)
        if (confirm.value) {
            deleting.value = request
        } else {
            scope.launch { delete(request) }
        }
    }

    fun cancel() {
        deleting.value = null
    }

    /**
     * Deletes whatever [request] queued and dismisses the dialog. [dontAskAgain] flips the
     * confirmation setting off, so the next delete skips the dialog.
     */
    fun confirm(dontAskAgain: Boolean) {
        val request = deleting.value ?: return
        deleting.value = null
        scope.launch {
            if (dontAskAgain) settings.update { it.copy(confirmVaultDelete = false) }
            delete(request)
        }
    }

    /** Brings back whatever the last grid delete moved to the trash. */
    fun undo() {
        val entries = undo.value ?: return
        undoWindow?.cancel()
        undo.value = null
        scope.launch {
            entries.forEach { vault.restoreTrashed(it.url) }
            notify(VaultNotice.Restored)
        }
    }

    private suspend fun delete(request: VaultDeleteRequest) {
        if (request.undoable) {
            trash(request.entries)
            return
        }
        var failed: VaultNotice? = null
        for (entry in request.entries) {
            vault.delete(entry.url)?.let { failed = VaultNotice.DeleteFailed(entry, it) }
        }
        notify(failed ?: VaultNotice.Deleted)
    }

    private suspend fun trash(entries: List<VaultEntry>) {
        // A second delete inside the window commits the first: one undo at a time.
        undoWindow?.cancel()
        vault.purgeTrash()
        val moved = entries.filter { entry ->
            when (val error = vault.trash(entry.url)) {
                null -> true
                else -> { notify(VaultNotice.DeleteFailed(entry, error)); false }
            }
        }
        undo.value = moved.ifEmpty { null }
        if (moved.isEmpty()) return
        undoWindow = scope.launch {
            delay(UNDO_WINDOW_MS)
            undo.value = null
            vault.purgeTrash()
        }
    }
}
