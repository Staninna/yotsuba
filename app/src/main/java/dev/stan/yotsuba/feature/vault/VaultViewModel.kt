package dev.stan.yotsuba.feature.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    /** Entries with a real file on disk, newest first. */
    val entries: List<SavedMediaEntity> = emptyList(),
    val rescanning: Boolean = false,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    savedMediaDao: SavedMediaDao,
    private val mediaVault: MediaVaultRepository,
) : ViewModel() {

    private val rescanning = MutableStateFlow(false)

    // Explorer position lives here, not in the composable: the adaptive layout switch on
    // rotation (nav bar ↔ nav rail) rebuilds the screen and would wipe remembered state.
    var boardKey by mutableStateOf<String?>(null)
    var threadKey by mutableStateOf<Long?>(null)
    /** URL of the entry open in the full-screen viewer, or null. */
    var viewingUrl by mutableStateOf<String?>(null)

    /**
     * When set, the viewer plays this url order instead of the current thread — the result of
     * a shuffle button. Reshuffled on every press; kept here so rotation replays the same order.
     */
    var shuffleOrder by mutableStateOf<List<String>?>(null)

    /** Viewer toggle: advance to the next item when a video ends instead of looping it. */
    var autoAdvance by mutableStateOf(false)

    /** Starts the viewer over [urls] in a fresh random order. */
    fun startShuffle(urls: List<String>) {
        if (urls.isEmpty()) return
        val order = urls.shuffled()
        shuffleOrder = order
        viewingUrl = order.first()
    }

    fun closeViewer() {
        viewingUrl = null
        shuffleOrder = null
    }

    val uiState: StateFlow<VaultUiState> = combine(savedMediaDao.all(), rescanning) { all, busy ->
        VaultUiState(entries = all.filter { it.absolutePath.isNotEmpty() }, rescanning = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun hasStorageAccess(): Boolean = mediaVault.hasStorageAccess()

    fun rescan() {
        if (rescanning.value) return
        viewModelScope.launch {
            rescanning.value = true
            mediaVault.migrateLegacyIfNeeded()
            mediaVault.rescan()
            rescanning.value = false
        }
    }

    suspend fun delete(url: String): Boolean = mediaVault.delete(url)
}
