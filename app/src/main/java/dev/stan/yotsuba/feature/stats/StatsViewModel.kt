package dev.stan.yotsuba.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.UsageStats
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.UsageRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The event fold plus the counts the existing tables already hold. */
data class StatsUiState(
    val usage: UsageStats,
    val historyCount: Int,
    val bookmarkCount: Int,
    val vaultFiles: Int,
    val vaultBytes: Long,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    usage: UsageRepository,
    history: HistoryRepository,
    bookmarks: BookmarkRepository,
    vault: MediaVaultRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState?> = combine(
        usage.events(), history.history, bookmarks.bookmarks, vault.entries(),
    ) { events, history, bookmarks, entries ->
        StatsUiState(
            usage = UsageStats.of(events, ZoneId.systemDefault()),
            historyCount = history.size,
            bookmarkCount = bookmarks.size,
            vaultFiles = entries.size,
            vaultBytes = entries.sumOf { it.sizeBytes ?: 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
