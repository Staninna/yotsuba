package dev.stan.yotsuba.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterMatcher
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    private val hiddenThreadsRepository: HiddenThreadsRepository,
) : ViewModel() {

    /**
     * The pager's pages, one per favourite board, in the order the user added them. Null until
     * settings have loaded so the empty state does not flash before the first read.
     */
    val boards: StateFlow<List<String>?> = settingsRepository.settings
        .map { it.favouriteBoards.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Boards the dice skips. In memory only: the veto lasts as long as this ViewModel does. */
    val vetoedBoards: StateFlow<Set<String>> get() = _vetoedBoards
    private val _vetoedBoards = MutableStateFlow(emptySet<String>())

    fun toggleVeto(board: String) = _vetoedBoards.update { if (board in it) it - board else it + board }

    /**
     * A random live thread from a random favourite the user has not vetoed, with hidden and
     * filtered threads left out the same way the catalog leaves them out. Null when no board
     * yields one, which includes every catalog failing to load.
     */
    suspend fun rollThread(): CatalogThread? {
        val settings = settingsRepository.settings.first()
        val matcher = FilterMatcher(settings.filters)
        return pickRandomThread(settings.favouriteBoards - _vetoedBoards.value) { board ->
            val threads = (catalogRepository.catalog(board) as? DataResult.Success)?.value ?: emptyList()
            val hidden = hiddenThreadsRepository.forBoard(board).first().map { it.threadNo }.toSet()
            val verdicts = matcher.verdicts(threads, board)
            threads.filter { it.no !in hidden && verdicts[it.no]?.action != FilterAction.HIDE }
        }
    }

    /**
     * Moves the favourite at [from] to slot [to], shifting the tabs between them by one. The
     * order is the set's iteration order, so it is rewritten as a fresh [LinkedHashSet].
     */
    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        settingsRepository.update { s ->
            val list = s.favouriteBoards.toMutableList()
            if (from == to || from !in list.indices || to !in list.indices) return@update s
            list.add(to, list.removeAt(from))
            s.copy(favouriteBoards = LinkedHashSet(list))
        }
    }

    /**
     * Drops [board] from the favourites and returns an undo that puts it back in its old
     * position rather than at the end.
     */
    fun removeFavourite(board: String): () -> Unit {
        // The undo waits for the removal to land, so it always sees the set it has to restore.
        val before = viewModelScope.async {
            var snapshot: Set<String> = emptySet()
            settingsRepository.update { s ->
                snapshot = s.favouriteBoards
                s.copy(favouriteBoards = s.favouriteBoards - board)
            }
            snapshot
        }
        return {
            viewModelScope.launch {
                val old = before.await()
                settingsRepository.update { s ->
                    // Keep anything favourited in the meantime, but restore the old order.
                    s.copy(favouriteBoards = old + (s.favouriteBoards - old))
                }
            }
        }
    }
}
