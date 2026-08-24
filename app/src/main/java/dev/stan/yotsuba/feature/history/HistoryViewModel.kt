package dev.stan.yotsuba.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryBucket { TODAY, YESTERDAY, THIS_WEEK, OLDER }

data class HistoryUiState(
    val groups: List<Pair<HistoryBucket, List<HistoryEntry>>> = emptyList(),
    val recordingEnabled: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.history, settingsRepository.settings,
    ) { entries, settings ->
        val zone = java.time.ZoneId.systemDefault()
        val startOfToday = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val groups = entries.groupBy { entry ->
            when {
                entry.viewedAt >= startOfToday -> HistoryBucket.TODAY
                entry.viewedAt >= startOfToday - 86_400_000 -> HistoryBucket.YESTERDAY
                entry.viewedAt >= startOfToday - 6 * 86_400_000 -> HistoryBucket.THIS_WEEK
                else -> HistoryBucket.OLDER
            }
        }.map { it.key to it.value }
        HistoryUiState(
            groups = groups,
            recordingEnabled = settings.recordHistory,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onRemove(entry: HistoryEntry) = viewModelScope.launch {
        historyRepository.remove(entry.board, entry.threadNo)
    }

    fun onUndoRemove(entry: HistoryEntry) = viewModelScope.launch {
        historyRepository.record(entry)
    }

    fun onClearAll() = viewModelScope.launch { historyRepository.clearAll() }
}
