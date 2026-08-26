package dev.stan.yotsuba.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.backup.BackupManager
import dev.stan.yotsuba.core.update.Release
import dev.stan.yotsuba.core.update.Updater
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val hiddenThreads: List<HiddenThread> = emptyList(),
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val hiddenThreadsRepository: HiddenThreadsRepository,
    private val boardRepository: BoardRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val updater: Updater,
    private val backups: BackupManager,
) : ViewModel() {

    private val _backupResult = MutableStateFlow<BackupManager.Result?>(null)
    val backupResult: StateFlow<BackupManager.Result?> = _backupResult

    fun onExportBackup() = viewModelScope.launch {
        _backupResult.value = backups.export(updater.currentVersion)
    }

    fun onImportBackup() = viewModelScope.launch {
        _backupResult.value = backups.import()
    }

    val updateState: StateFlow<Updater.State> = updater.state
    val updaterVersion: String get() = updater.currentVersion

    fun canInstallPackages(): Boolean = updater.canInstallPackages()
    fun unknownSourcesIntent() = updater.unknownSourcesIntent()
    fun onDismissUpdate() = updater.dismiss()

    fun onCheckForUpdates() = viewModelScope.launch {
        updater.check(settingsRepository.settings.first().updateToken)
    }

    fun onInstallUpdate(release: Release) = viewModelScope.launch {
        updater.downloadAndInstall(release, settingsRepository.settings.first().updateToken)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, hiddenThreadsRepository.all,
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

    fun onClearCache() = viewModelScope.launch { maintenanceRepository.clearCaches() }
    fun onClearHistory() = viewModelScope.launch { historyRepository.clearAll() }
    fun onClearBookmarks() = viewModelScope.launch { bookmarkRepository.clearAll() }
    fun onClearTrustedDomains() = update { it.copy(trustedDomains = emptySet()) }
    fun onRevokeTrustedDomain(domain: String) = update { it.copy(trustedDomains = it.trustedDomains - domain) }
    fun onUnhideThread(hidden: HiddenThread) = viewModelScope.launch {
        hiddenThreadsRepository.unhide(hidden.board, hidden.threadNo)
    }
}
