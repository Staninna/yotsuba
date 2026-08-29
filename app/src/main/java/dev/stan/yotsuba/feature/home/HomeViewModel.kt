package dev.stan.yotsuba.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * The pager's pages, one per favourite board, in the order the user added them. Null until
     * settings have loaded so the empty state does not flash before the first read.
     */
    val boards: StateFlow<List<String>?> = settingsRepository.settings
        .map { it.favouriteBoards.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Drops [board] from the favourites and returns an undo that puts it back in its old
     * position rather than at the end.
     */
    fun removeFavourite(board: String): () -> Unit {
        var before: Set<String> = emptySet()
        viewModelScope.launch {
            settingsRepository.update { s ->
                before = s.favouriteBoards
                s.copy(favouriteBoards = s.favouriteBoards - board)
            }
        }
        return {
            viewModelScope.launch {
                settingsRepository.update { s ->
                    // Keep anything favourited in the meantime, but restore the old order.
                    s.copy(favouriteBoards = before + (s.favouriteBoards - before))
                }
            }
        }
    }
}
