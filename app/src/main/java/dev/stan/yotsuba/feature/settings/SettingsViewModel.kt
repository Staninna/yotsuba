package dev.stan.yotsuba.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.core.update.Release
import dev.stan.yotsuba.core.update.Updater
import dev.stan.yotsuba.domain.model.DataResult
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How a clear-* action ended, so the snackbar reports the work rather than the tap. */
sealed interface ClearResult {
    data object Done : ClearResult
    data class Failed(val message: String) : ClearResult
}

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
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _restoreAvailable = MutableStateFlow<BackupInfo?>(null)

    /** A backup at the vault root on an install that has nothing to lose to a restore; null once dismissed. */
    val restoreAvailable: StateFlow<BackupInfo?> = _restoreAvailable.asStateFlow()

    private val _backupResult = MutableStateFlow<BackupResult?>(null)

    /** The last manual export or import outcome, cleared by [onBackupResultShown]. */
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private var restoreProbed = false

    /**
     * Looks for a restorable backup the first time the Storage section is on screen. Only that
     * section can show the result, so the other sections never pay for the shared-storage read,
     * and a dismissed banner stays dismissed for the life of this ViewModel.
     */
    fun onStorageSectionShown() {
        if (restoreProbed) return
        restoreProbed = true
        viewModelScope.launch {
            if (backupRepository.isFreshInstall()) _restoreAvailable.value = backupRepository.available()
        }
    }

    private val _backupBusy = MutableStateFlow(false)

    /** True while an export or import is running, so the rows can refuse a second tap. */
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    fun onExportBackup() = backup { backupRepository.export() }

    fun onImportBackup() = backup {
        backupRepository.import().also { _restoreAvailable.value = null }
    }

    private fun backup(run: suspend () -> BackupResult) = viewModelScope.launch {
        if (_backupBusy.value) return@launch
        _backupBusy.value = true
        try {
            _backupResult.value = run()
        } finally {
            _backupBusy.value = false
        }
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

    private val _clearResult = MutableStateFlow<ClearResult?>(null)

    /** The last clear-* outcome, cleared by [onClearResultShown]. */
    val clearResult: StateFlow<ClearResult?> = _clearResult.asStateFlow()

    fun onClearResultShown() { _clearResult.value = null }

    fun onClearCache() = clearing { maintenanceRepository.clearCaches() }
    fun onClearHistory() = clearing { historyRepository.clearAll() }
    fun onClearBookmarks() = clearing { bookmarkRepository.clearAll() }
    fun onClearTrustedDomains() = clearing { settingsRepository.update { it.copy(trustedDomains = emptySet()) } }

    private fun clearing(work: suspend () -> Unit) = viewModelScope.launch {
        _clearResult.value = try {
            work()
            ClearResult.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ClearResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    fun onUnhideThread(hidden: HiddenThread) = viewModelScope.launch {
        hiddenThreadsRepository.unhide(hidden.board, hidden.threadNo)
    }
}
