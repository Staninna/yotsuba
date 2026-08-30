package dev.stan.yotsuba.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.repository.ReverseSearchRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs the upload behind a local-file reverse search, so it survives recomposition and
 * dies with the screen. The sheet renders [state] and calls [search]; the screen opens
 * the URL an [LocalSearchState.Opened] carries.
 */
@HiltViewModel
class ReverseSearchViewModel @Inject constructor(
    private val uploads: ReverseSearchRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LocalSearchState>(LocalSearchState.Idle)
    val state: StateFlow<LocalSearchState> = _state

    private var job: Job? = null

    /**
     * Sends [file] to [engine]: through its own upload form when the setting says so and
     * the form yields a URL, otherwise through the temporary host. The host never gets the
     * file on a plain engine tap: that only parks the state at [LocalSearchState.ConfirmHost]
     * and waits. [hostConfirmed] is the tap on the confirm row, or on the "try the host
     * instead" retry after a failed direct upload.
     */
    fun search(engine: ReverseSearchEngine, file: File, ext: String, hostConfirmed: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val direct = engine.directUpload?.takeIf {
                !hostConfirmed && settings.localSearchMethod == LocalSearchMethod.DIRECT_UPLOAD
            }
            // Decide before showing anything, so the spinner never precedes the question.
            if (direct == null && !hostConfirmed && settings.confirmTemporaryHost) {
                _state.value = LocalSearchState.ConfirmHost(engine)
                return@launch
            }
            _state.value = LocalSearchState.Uploading(engine)
            val result = if (direct != null) {
                uploads.directSearchUrl(direct, file, ext)
            } else {
                when (val hosted = uploads.hostTemporarily(file, ext)) {
                    is DataResult.Success -> DataResult.Success(engine.searchUrl(hosted.value.url))
                    is DataResult.Failure -> hosted
                }
            }
            _state.value = when (result) {
                is DataResult.Success -> LocalSearchState.Opened(engine, result.value)
                is DataResult.Failure -> LocalSearchState.Failed(engine, canFallback = direct != null)
            }
        }
    }

    /** "Don't ask again" on the dialog; the Privacy section can turn it back on. */
    fun stopConfirmingHost() {
        viewModelScope.launch { settingsRepository.update { it.copy(confirmTemporaryHost = false) } }
    }

    /** The user declined the host; back to a plain sheet. */
    fun declineHost() {
        if (_state.value is LocalSearchState.ConfirmHost) _state.value = LocalSearchState.Idle
    }

    /** Stops whatever is in flight and clears the sheet's state; dismissing calls this. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = LocalSearchState.Idle
    }
}
