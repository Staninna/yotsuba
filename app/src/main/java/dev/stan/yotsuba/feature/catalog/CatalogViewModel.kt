package dev.stan.yotsuba.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.di.ComputeDispatcher
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.toUiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogSort
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterMatcher
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.domain.model.removedCount
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    historyRepository: HistoryRepository,
    private val threadSiblings: ThreadSiblingsStore,
    aboutCards: BoardAboutCards,
    networkMonitor: NetworkMonitor,
    /** Where the filter pipeline runs; tests pass their scheduler's dispatcher. */
    @ComputeDispatcher private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(@dagger.assisted.Assisted("board") board: String, @dagger.assisted.Assisted("search") initialSearch: String?): CatalogViewModel
    }

    private val result = LoadableFlow(viewModelScope) { catalogRepository.catalog(board, it) }
    /** null = search closed; the list is unfiltered. */
    private val searchQuery = MutableStateFlow(initialSearch?.takeIf { it.isNotBlank() })
    private val refreshing = MutableStateFlow(false)
    /** Threads whose blurred thumbnail was tapped open; lasts as long as this ViewModel. */
    private val unblurred = MutableStateFlow(emptySet<Long>())
    private val hiddenNos = hiddenThreadsRepository.forBoard(board)
        .map { list -> list.map { it.threadNo }.toSet() }
    /** Read mark per visited thread on this board, for the "+N new" badge. */
    private val readMarks = historyRepository.history
        .map { entries -> entries.filter { it.board == board }.associate { it.threadNo to it.readUpTo } }
        .distinctUntilChanged()
    // Collected only while uiState has subscribers, so the system network callback is
    // registered for as long as the catalog is on screen, not for the ViewModel's lifetime.
    private val offline = networkMonitor.status.map { it == NetworkStatus.Offline }.distinctUntilChanged()

    /** Board metadata for the top bar; not part of the list pipeline. */
    private val _boardInfo = MutableStateFlow<Board?>(null)
    val boardInfo: StateFlow<Board?> = _boardInfo
    /** The About card is claimed once per board per session, by whichever ViewModel gets there first. */
    private val aboutDismissed = MutableStateFlow(!aboutCards.claim(board))
    private val about = combine(_boardInfo, aboutDismissed) { info, dismissed ->
        info?.description?.takeIf { !dismissed && it.isNotBlank() }
    }

    /**
     * Where the grid was when its pane last left composition, as (first visible item, pixel
     * offset). The pane's own saveable state covers process death and back navigation, but a
     * Home page swiped out of the pager loses it once the tab is switched away and back; this
     * outlives the pane because the ViewModel is keyed by board under the screen that hosts it.
     */
    var scrollPosition: Pair<Int, Int> = 0 to 0

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

    /** The slice of settings the list depends on; compared by value so the matcher compiles once per change. */
    private data class Prefs(
        val layout: CatalogLayout,
        val sort: CatalogSort,
        val blur: Boolean,
        val filters: List<Filter>,
        val fontSize: FontSize,
        val lineSpacing: LineSpacing,
    )

    /** Everything the list is derived from besides the fetch result and the user's own toggles. */
    private data class Inputs(
        val prefs: Prefs,
        val matcher: FilterMatcher,
        val hidden: Set<Long>,
        val offline: Boolean,
        val readMarks: Map<Long, Long>,
        val about: String?,
    )

    private val inputs = combine(
        settingsRepository.settings
            .map { it.forBoard(board) }
            .map { Prefs(it.catalogLayout, it.catalogSorts[board] ?: CatalogSort.BUMP_ORDER, it.blurThumbnails, it.filters, it.fontSize, it.lineSpacing) }
            .distinctUntilChanged()
            .map { it to FilterMatcher(it.filters) },
        hiddenNos,
        offline,
        readMarks,
        about,
    ) { (prefs, matcher), hidden, offline, readMarks, about -> Inputs(prefs, matcher, hidden, offline, readMarks, about) }

    val uiState: StateFlow<UiState<CatalogContent>> = combine(
        result.flow, searchQuery, refreshing, inputs, unblurred,
    ) { res, query, isRefreshing, i, unblurred ->
        res.toUiState { threads ->
            val searched = threads
                .filter { it.no !in i.hidden }
                .filter {
                    query.isNullOrBlank() ||
                        it.subject?.contains(query, true) == true ||
                        it.excerpt.plainText.contains(query, true)
                }
            val verdicts = i.matcher.verdicts(searched, board)
            val shown = searched.filterNot { verdicts[it.no]?.action == FilterAction.HIDE }.sortedBy(i.prefs.sort)
            CatalogContent(
                threads = shown,
                layout = i.prefs.layout,
                sort = i.prefs.sort,
                blurred = if (i.prefs.blur) shown.mapTo(mutableSetOf()) { it.no } - unblurred else emptySet(),
                searchQuery = query,
                refreshing = isRefreshing,
                offline = i.offline,
                about = i.about,
                stubs = verdicts.filterValues { it.action == FilterAction.STUB },
                faded = verdicts.filterValues { it.action == FilterAction.FADE }.keys,
                filteredCount = verdicts.removedCount,
                crossReferences = crossReferences(threads),
                fontSize = i.prefs.fontSize,
                lineSpacing = i.prefs.lineSpacing,
                newReplies = shown.mapNotNull { t ->
                    i.readMarks[t.no]?.let { mark -> t.newRepliesSince(mark)?.let { t.no to it } }
                }.toMap(),
            )
        }
    }
        .flowOn(compute)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun onSearchChange(query: String) { searchQuery.value = query }
    fun onOpenSearch() { if (searchQuery.value == null) searchQuery.value = "" }
    fun onCloseSearch() { searchQuery.value = null }

    fun onCycleLayout() = viewModelScope.launch {
        settingsRepository.update { s ->
            val next = CatalogLayout.entries[(s.catalogLayout.ordinal + 1) % CatalogLayout.entries.size]
            s.copy(catalogLayout = next)
        }
    }

    fun onRevealThumbnail(threadNo: Long) { unblurred.value += threadNo }
    fun onDismissAbout() { aboutDismissed.value = true }

    /** Bump order is the default, so choosing it drops the board's entry rather than storing it. */
    fun onSelectSort(sort: CatalogSort) = viewModelScope.launch {
        settingsRepository.update { s ->
            s.copy(catalogSorts = if (sort == CatalogSort.BUMP_ORDER) s.catalogSorts - board else s.catalogSorts + (board to sort))
        }
    }

    /**
     * A thread card was tapped: remember the list as it is on screen right now, so the thread
     * can be swiped to the ones beside it. Nothing is recorded while the catalog is not loaded.
     */
    fun onThreadOpened(threadNo: Long) {
        val threads = (uiState.value as? UiState.Success)?.data?.threads ?: return
        if (threads.none { it.no == threadNo }) return
        threadSiblings.record(board, threads.map { it.no })
    }

    fun onHideThread(threadNo: Long) = viewModelScope.launch {
        hiddenThreadsRepository.hide(board, threadNo)
    }

    fun onUndoHide(threadNo: Long) = viewModelScope.launch {
        hiddenThreadsRepository.unhide(board, threadNo)
    }
}
