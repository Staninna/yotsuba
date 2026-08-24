package dev.stan.yotsuba.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class SettingsUiState(
    val settings: Settings = Settings(),
    val hiddenThreads: List<HiddenThreadEntity> = emptyList(),
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkDao: BookmarkDao,
    private val hiddenThreadDao: HiddenThreadDao,
    private val boardRepository: BoardRepository,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, hiddenThreadDao.all(),
    ) { settings, hidden ->
        SettingsUiState(
            settings = settings,
            hiddenThreads = hidden,
            versionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (Settings) -> Settings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    /** "Hide NSFW boards" is a bulk action over ws_board = 0, not a second filter (D13). */
    fun onHideNsfwBoards() = viewModelScope.launch {
        val boards = (boardRepository.boards() as? DataResult.Success)?.value.orEmpty()
        val nsfw = boards.filter { !it.worksafe }.map { it.code }.toSet()
        settingsRepository.update { it.copy(hiddenBoards = it.hiddenBoards + nsfw) }
    }

    fun onClearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching { okHttpClient.cache?.evictAll() }
            File(context.cacheDir, "image_cache").deleteRecursively()
        }
    }

    fun onClearHistory() = viewModelScope.launch { historyRepository.clearAll() }
    fun onClearBookmarks() = viewModelScope.launch { bookmarkDao.clearAll() }
    fun onClearTrustedDomains() = update { it.copy(trustedDomains = emptySet()) }
    fun onRevokeTrustedDomain(domain: String) = update { it.copy(trustedDomains = it.trustedDomains - domain) }
    fun onUnhideThread(entity: HiddenThreadEntity) = viewModelScope.launch {
        hiddenThreadDao.unhide(entity.board, entity.threadNo)
    }
}
