package dev.stan.yotsuba.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryBucket { TODAY, YESTERDAY, THIS_WEEK, OLDER }

/** One section of the history list: a date bucket and the rows that fall in it. */
data class HistoryGroup(val bucket: HistoryBucket, val entries: List<HistoryEntry>)

private const val DAY_MS = 86_400_000L

/** Which section a visit at [viewedAt] belongs to, given local midnight in epoch millis. */
fun bucketOf(viewedAt: Long, startOfToday: Long): HistoryBucket = when {
    viewedAt >= startOfToday -> HistoryBucket.TODAY
    viewedAt >= startOfToday - DAY_MS -> HistoryBucket.YESTERDAY
    viewedAt >= startOfToday - 6 * DAY_MS -> HistoryBucket.THIS_WEEK
    else -> HistoryBucket.OLDER
}

/** Groups [entries] by bucket, keeping the repository's newest-first order within each. */
fun groupHistory(entries: List<HistoryEntry>, startOfToday: Long): List<HistoryGroup> =
    entries.groupBy { bucketOf(it.viewedAt, startOfToday) }
        .map { (bucket, rows) -> HistoryGroup(bucket, rows) }
        .sortedBy { it.bucket.ordinal }

/** Emits once at each local midnight so the sections re-bucket when the date rolls over. */
fun midnightTicker(clock: Clock): Flow<Unit> = flow {
    while (true) {
        val now = ZonedDateTime.now(clock)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000))
        emit(Unit)
    }
}

data class HistoryUiState(
    val groups: List<HistoryGroup> = emptyList(),
    val recordingEnabled: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    /** Fires whenever the local date may have changed; tests drive it by hand. */
    dateTicks: Flow<Unit>,
) : ViewModel() {

    @Inject constructor(
        historyRepository: HistoryRepository,
        settingsRepository: SettingsRepository,
    ) : this(
        historyRepository,
        settingsRepository,
        Clock.systemDefaultZone(),
        midnightTicker(Clock.systemDefaultZone()),
    )

    private val startOfToday: Flow<Long> = dateTicks
        .onStart { emit(Unit) }
        .map { LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli() }

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.history, settingsRepository.settings, startOfToday,
    ) { entries, settings, today ->
        HistoryUiState(
            groups = groupHistory(entries, today),
            recordingEnabled = settings.recordHistory,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onRemove(entry: HistoryEntry) = viewModelScope.launch {
        historyRepository.remove(entry.board, entry.threadNo)
    }

    fun onUndoRemove(entry: HistoryEntry) = viewModelScope.launch {
        historyRepository.restore(entry)
    }

    fun onClearAll() = viewModelScope.launch { historyRepository.clearAll() }
}
