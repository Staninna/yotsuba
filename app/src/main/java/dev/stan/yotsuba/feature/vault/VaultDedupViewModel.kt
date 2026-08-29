package dev.stan.yotsuba.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.DedupMode
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
    data class Backfilling(val done: Int, val total: Int) : DedupPhase
    data object Scanning : DedupPhase
    data class Ready(val groups: List<DuplicateGroup>) : DedupPhase
    data class Deleting(val done: Int, val total: Int) : DedupPhase
}

data class DedupState(
    val phase: DedupPhase = DedupPhase.Idle,
    val mode: DedupMode = DedupMode.EXACT,
    val maxDistance: Int = VaultDedupRepository.DEFAULT_MAX_DISTANCE,
    /** Per group (by keeper url), the urls the user wants to keep. Defaults to the suggested keeper. */
    val kept: Map<String, Set<String>> = emptyMap(),
    /** Files removed by the last delete pass; the sheet reports it once. */
    val lastDeleted: Int? = null,
    val lastFailed: Int = 0,
) {
    val groups: List<DuplicateGroup> get() = (phase as? DedupPhase.Ready)?.groups.orEmpty()

    fun keptIn(group: DuplicateGroup): Set<String> = kept[group.keeperUrl] ?: setOf(group.keeperUrl)

    /** Everything the suggestions would remove, across all groups, at the suggested keepers. */
    val suggestedRemovals: List<String> get() = groups.flatMap { g -> g.redundant.map { it.url } }
    val suggestedBytes: Long get() = groups.sumOf { it.redundantBytes }
}

@HiltViewModel
class VaultDedupViewModel @Inject constructor(
    private val dedup: VaultDedupRepository,
    private val vault: MediaVaultRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DedupState())
    val state: StateFlow<DedupState> = _state
    private var run: Job? = null

    /** Backfills whatever lacks a hash, then scans. Safe to call again: it restarts. */
    fun start() {
        run?.cancel()
        run = viewModelScope.launch {
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
        val groups = dedup.findDuplicates(s.mode, s.maxDistance)
        _state.update { it.copy(phase = DedupPhase.Ready(groups)) }
    }

    fun toggleKept(group: DuplicateGroup, url: String) {
        _state.update { s ->
            val current = s.keptIn(group)
            val next = if (url in current) current - url else current + url
            s.copy(kept = s.kept + (group.keeperUrl to next))
        }
    }

    /** Deletes everything in [group] not marked kept. Keeping nothing is refused. */
    fun applyGroup(group: DuplicateGroup) {
        val kept = _state.value.keptIn(group)
        if (kept.isEmpty()) return
        delete(group.entries.map { it.url }.filterNot { it in kept })
    }

    /** Deletes every suggested redundant file across all groups. */
    fun applyAllSuggestions() = delete(_state.value.suggestedRemovals)

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
