package dev.stan.yotsuba.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.filter.FilterMatcher
import dev.stan.yotsuba.core.filter.FilterableFields
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CatalogViewModel.Factory::class)
class CatalogViewModel @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted("board") private val board: String,
    @dagger.assisted.Assisted("search") private val initialSearch: String?,
    private val catalogRepository: CatalogRepository,
    private val boardRepository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val hiddenThreadsRepository: HiddenThreadsRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(@dagger.assisted.Assisted("board") board: String, @dagger.assisted.Assisted("search") initialSearch: String?): CatalogViewModel
    }

    private val result = LoadableFlow(viewModelScope) { catalogRepository.catalog(board, it) }
    /** null = search closed; the list is unfiltered. */
    private val searchQuery = MutableStateFlow(initialSearch?.takeIf { it.isNotBlank() })
    private val refreshing = MutableStateFlow(false)
    private val hiddenNos = hiddenThreadsRepository.forBoard(board)
        .map { list -> list.map { it.threadNo }.toSet() }
    private val networkStatus = networkMonitor.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkStatus.Unmetered)

    /** Board metadata for the top bar; not part of the list pipeline. */
    private val _boardInfo = MutableStateFlow<Board?>(null)
    val boardInfo: StateFlow<Board?> = _boardInfo

    init {
        load()
        viewModelScope.launch { _boardInfo.value = boardRepository.board(board) }
    }

    fun load(forceRefresh: Boolean = false): Job {
        val job = result.load(forceRefresh)
        if (forceRefresh) {
            refreshing.value = true
            job.invokeOnCompletion { refreshing.value = false }
        }
        return job
    }

    /** Error-state retry: bypass the cache like pull-to-refresh, but show the loading shell. */
    fun retry(): Job = result.load(forceRefresh = true, showLoading = true)

    private val layout = settingsRepository.settings.map { it.catalogLayout }
    private val offline = networkStatus.map { it == NetworkStatus.Offline }
    /** Compiled once per change to the filter list, not once per thread. */
    private val matcher = settingsRepository.settings.map { it.filters }.distinctUntilChanged()
        .map { FilterMatcher(it) }

    val uiState: StateFlow<UiState<CatalogContent>> = combine(
        result.flow, searchQuery, refreshing, layout, combine(hiddenNos, offline, matcher, ::Triple),
    ) { res, query, isRefreshing, layout, (hidden, offline, matcher) ->
        when (res) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(res.error)
            is DataResult.Success -> {
                val searched = res.value
                    .filter { it.no !in hidden }
                    .filter {
                        query.isNullOrBlank() ||
                            it.subject?.contains(query, true) == true ||
                            it.excerpt.plainText.contains(query, true)
                    }
                val stubs = mutableMapOf<Long, Filter>()
                var hiddenByFilter = 0
                val visible = searched.filter { thread ->
                    val match = matcher.matches(FilterableFields.of(thread), board)
                    when (match?.action) {
                        null -> true
                        FilterAction.STUB -> { stubs[thread.no] = match; true }
                        FilterAction.HIDE -> { hiddenByFilter++; false }
                    }
                }
                UiState.Success(CatalogContent(
                    threads = visible,
                    layout = layout,
                    searchQuery = query,
                    refreshing = isRefreshing,
                    offline = offline,
                    stubs = stubs,
                    filteredCount = hiddenByFilter + stubs.size,
                ))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun onSearchChange(query: String) { searchQuery.value = query }
    fun onOpenSearch() { if (searchQuery.value == null) searchQuery.value = "" }
    fun onCloseSearch() { searchQuery.value = null }

    fun onCycleLayout() = viewModelScope.launch {
        settingsRepository.update { s ->
            val next = CatalogLayout.entries[(s.catalogLayout.ordinal + 1) % CatalogLayout.entries.size]
            s.copy(catalogLayout = next)
        }
    }

    fun onHideThread(threadNo: Long) = viewModelScope.launch {
        hiddenThreadsRepository.hide(board, threadNo)
    }

    fun onUndoHide(threadNo: Long) = viewModelScope.launch {
        hiddenThreadsRepository.unhide(board, threadNo)
    }
}
