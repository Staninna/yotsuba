package dev.stan.yotsuba.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.model.Board
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CatalogViewModel.Factory::class)
class CatalogViewModel @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted("board") private val board: String,
    @dagger.assisted.Assisted("search") private val initialSearch: String?,
    private val catalogRepository: CatalogRepository,
    private val boardRepository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val hiddenThreadDao: HiddenThreadDao,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(@dagger.assisted.Assisted("board") board: String, @dagger.assisted.Assisted("search") initialSearch: String?): CatalogViewModel
    }

    private val result = LoadableFlow(viewModelScope) { catalogRepository.catalog(board, it) }
    private val boardInfo = MutableStateFlow<Board?>(null)
    private val searchQuery = MutableStateFlow(initialSearch.orEmpty())
    private val refreshing = MutableStateFlow(false)
    private val networkStatus = networkMonitor.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkStatus.Unmetered)

    init {
        load()
        viewModelScope.launch { boardInfo.value = boardRepository.board(board) }
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) refreshing.value = true
            result.load(forceRefresh).join()
            refreshing.value = false
        }
    }

    val uiState: StateFlow<UiState<CatalogContent>> = combine(
        result.flow, boardInfo, settingsRepository.settings, searchQuery, refreshing,
        hiddenThreadDao.forBoard(board), networkStatus,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val res = values[0] as DataResult<List<CatalogThread>>?
        val info = values[1] as Board?
        val settings = values[2] as dev.stan.yotsuba.domain.model.Settings
        val query = values[3] as String
        val isRefreshing = values[4] as Boolean
        val hidden = (values[5] as List<HiddenThreadEntity>).map { it.threadNo }.toSet()
        val status = values[6] as NetworkStatus
        when (res) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(res.error)
            is DataResult.Success -> {
                val filtered = res.value
                    .filter { it.no !in hidden }
                    .filter {
                        query.isBlank() ||
                            it.subject?.contains(query, true) == true ||
                            it.excerpt.plainText.contains(query, true)
                    }
                UiState.Success(CatalogContent(
                    board = info,
                    threads = filtered,
                    layout = settings.catalogLayout,
                    searchQuery = query,
                    refreshing = isRefreshing,
                    offline = status == NetworkStatus.Offline,
                ))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun onSearchChange(query: String) { searchQuery.value = query }

    fun onCycleLayout() = viewModelScope.launch {
        settingsRepository.update { s ->
            val next = CatalogLayout.entries[(s.catalogLayout.ordinal + 1) % CatalogLayout.entries.size]
            s.copy(catalogLayout = next)
        }
    }

    fun onHideThread(threadNo: Long) = viewModelScope.launch {
        hiddenThreadDao.hide(HiddenThreadEntity(board, threadNo, System.currentTimeMillis()))
    }

    fun onUndoHide(threadNo: Long) = viewModelScope.launch {
        hiddenThreadDao.unhide(board, threadNo)
    }
}
