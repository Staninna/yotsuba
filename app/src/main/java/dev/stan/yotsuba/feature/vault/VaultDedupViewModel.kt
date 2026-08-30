package dev.stan.yotsuba.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the duplicate finder is in its run. */
sealed interface DedupPhase {
    data object Idle : DedupPhase
    /** The vault index is being rebuilt from disk before anything is hashed. */
    data object Rescanning : DedupPhase
    data class Backfilling(val done: Int, val total: Int) : DedupPhase
    data object Scanning : DedupPhase
    data class Ready(val groups: List<DuplicateGroup>) : DedupPhase
    data class Deleting(val done: Int, val total: Int) : DedupPhase
}

data class DedupState(
    val phase: DedupPhase = DedupPhase.Idle,
    val mode: DedupMode = DedupMode.EXACT,
    val maxDistance: Int = VaultDedupRepository.DEFAULT_MAX_DISTANCE,
    /** URLs the finder is limited to (the board or thread on screen); null means the whole vault. */
    val scope: Set<String>? = null,
    /** Per group (by keeper url), the urls the user wants to keep. Defaults to the suggested keeper. */
    val kept: Map<String, Set<String>> = emptyMap(),
    /** Files removed by the last delete pass; the sheet reports it once. */
    val lastDeleted: Int? = null,
    val lastFailed: Int = 0,
) {
    val groups: List<DuplicateGroup> get() = (phase as? DedupPhase.Ready)?.groups.orEmpty()

    fun keptIn(group: DuplicateGroup): Set<String> = kept[group.keeperUrl] ?: setOf(group.keeperUrl)

    /** What applying [group] deletes: everything not ticked. A group with nothing ticked is left alone. */
    fun removalsIn(group: DuplicateGroup): List<DuplicateEntry> {
        val kept = keptIn(group)
        return if (kept.isEmpty()) emptyList() else group.entries.filterNot { it.url in kept }
    }

    /** Everything "apply all" deletes, honouring whatever the user re-ticked in each group. */
    val removals: List<DuplicateEntry> get() = groups.flatMap(::removalsIn)
    val removalBytes: Long get() = removals.sumOf { it.sizeBytes }
}

@HiltViewModel
class VaultDedupViewModel @Inject constructor(
    private val dedup: VaultDedupRepository,
    private val vault: MediaVaultRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DedupState())
    val state: StateFlow<DedupState> = _state
    private var run: Job? = null

    /**
     * Rebuilds the index from disk (a synced folder may have changed under the app), hashes
     * whatever lacks a hash, then scans, limited to [scope] when one is given. Safe to call
     * again: it restarts.
     */
    fun start(scope: Set<String>? = null) {
        run?.cancel()
        run = viewModelScope.launch {
            _state.update { it.copy(scope = scope, phase = DedupPhase.Rescanning) }
            vault.rescan()
            if (dedup.missingHashCount() > 0) {
                _state.update { it.copy(phase = DedupPhase.Backfilling(0, 0)) }
                dedup.backfillHashes { done, total ->
                    _state.update { it.copy(phase = DedupPhase.Backfilling(done, total)) }
                }
            }
            scan()
        }
    }

    fun setMode(mode: DedupMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        rescan()
    }

    fun setMaxDistance(distance: Int) {
        _state.update { it.copy(maxDistance = distance) }
    }

    /** Re-runs the scan with the current mode and distance; the slider commits through this. */
    fun rescan() {
        run?.cancel()
        run = viewModelScope.launch { scan() }
    }

    private suspend fun scan() {
        _state.update { it.copy(phase = DedupPhase.Scanning, kept = emptyMap()) }
        val s = _state.value
        val groups = dedup.findDuplicates(s.mode, s.maxDistance).inScope(s.scope)
        _state.update { it.copy(phase = DedupPhase.Ready(groups)) }
    }

    /**
     * Groups cut down to [scope]. A group that keeps fewer than two entries is no longer a
     * duplicate the user can act on here; a keeper that fell outside the scope is replaced
     * by the first entry left, so every group still has something to keep.
     */
    private fun List<DuplicateGroup>.inScope(scope: Set<String>?): List<DuplicateGroup> {
        if (scope == null) return this
        return mapNotNull { group ->
            val entries = group.entries.filter { it.url in scope }
            if (entries.size < 2) return@mapNotNull null
            val keeper = if (group.keeperUrl in scope) group.keeperUrl else entries.first().url
            DuplicateGroup(entries, keeper)
        }
    }

    fun toggleKept(group: DuplicateGroup, url: String) {
        _state.update { s ->
            val current = s.keptIn(group)
            val next = if (url in current) current - url else current + url
            s.copy(kept = s.kept + (group.keeperUrl to next))
        }
    }

    /** Deletes everything in [group] not marked kept. Keeping nothing is refused. */
    fun applyGroup(group: DuplicateGroup) = delete(_state.value.removalsIn(group).map { it.url })

    /** Deletes every unticked file across all groups. */
    fun applyAll() = delete(_state.value.removals.map { it.url })

    fun noticeShown() = _state.update { it.copy(lastDeleted = null, lastFailed = 0) }

    private fun delete(urls: List<String>) {
        if (urls.isEmpty()) return
        run?.cancel()
        run = viewModelScope.launch {
            _state.update { it.copy(phase = DedupPhase.Deleting(0, urls.size)) }
            var failed = 0
            urls.forEachIndexed { i, url ->
                if (vault.delete(url) != null) failed++
                _state.update { it.copy(phase = DedupPhase.Deleting(i + 1, urls.size)) }
            }
            _state.update { it.copy(lastDeleted = urls.size - failed, lastFailed = failed) }
            scan()
        }
    }
}
