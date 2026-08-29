package dev.stan.yotsuba.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.core.update.Release
import dev.stan.yotsuba.core.update.Updater
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BackupResult
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val hiddenThreads: List<HiddenThread> = emptyList(),
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val hiddenThreadsRepository: HiddenThreadsRepository,
    private val boardRepository: BoardRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val updater: Updater,
    private val backupRepository: BackupRepository = BackupRepository.None,
) : ViewModel() {

    private val _restoreAvailable = MutableStateFlow<BackupInfo?>(null)

    /** A backup at the vault root on an install that has nothing to lose to a restore; null once dismissed. */
    val restoreAvailable: StateFlow<BackupInfo?> = _restoreAvailable.asStateFlow()

    private val _backupResult = MutableStateFlow<BackupResult?>(null)

    /** The last manual export or import outcome, cleared by [onBackupResultShown]. */
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    init {
        viewModelScope.launch {
            if (backupRepository.isFreshInstall()) _restoreAvailable.value = backupRepository.available()
        }
    }

    fun onExportBackup() = viewModelScope.launch { _backupResult.value = backupRepository.export() }

    fun onImportBackup() = viewModelScope.launch {
        _backupResult.value = backupRepository.import()
        _restoreAvailable.value = null
    }

    fun onDismissRestore() { _restoreAvailable.value = null }
    fun onBackupResultShown() { _backupResult.value = null }

    val updateState: StateFlow<Updater.State> = updater.state

    fun canInstallPackages(): Boolean = updater.canInstallPackages()
    fun unknownSourcesIntent() = updater.unknownSourcesIntent()

    fun onCheckForUpdates() = viewModelScope.launch { updater.check() }

    fun onInstallUpdate(release: Release) = viewModelScope.launch {
        updater.downloadAndInstall(release)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, hiddenThreadsRepository.all,
    ) { settings, hidden ->
        SettingsUiState(
            settings = settings,
            hiddenThreads = hidden,
            versionName = BuildConfig.VERSION_NAME,
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

    fun onClearCache() = viewModelScope.launch { maintenanceRepository.clearCaches() }
    fun onClearHistory() = viewModelScope.launch { historyRepository.clearAll() }
    fun onClearBookmarks() = viewModelScope.launch { bookmarkRepository.clearAll() }
    fun onUnhideThread(hidden: HiddenThread) = viewModelScope.launch {
        hiddenThreadsRepository.unhide(hidden.board, hidden.threadNo)
    }
}
