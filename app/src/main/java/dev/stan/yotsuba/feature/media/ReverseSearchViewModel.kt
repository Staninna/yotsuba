package dev.stan.yotsuba.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val uploader: ReverseSearchUploader,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LocalSearchState>(LocalSearchState.Idle)
    val state: StateFlow<LocalSearchState> = _state

    private var job: Job? = null

    /**
     * Sends [file] to [engine]: through its own upload form when the setting says so and
     * the form yields a URL, otherwise through the temporary host. [forceHost] is the
     * "try the host instead" retry after a failed direct upload.
     */
    fun search(engine: ReverseSearchEngine, file: File, ext: String, forceHost: Boolean = false) {
        job?.cancel()
        _state.value = LocalSearchState.Uploading(engine)
        job = viewModelScope.launch {
            val method = settingsRepository.settings.first().localSearchMethod
            val direct = !forceHost &&
                method == LocalSearchMethod.DIRECT_UPLOAD &&
                engine.uploadStyle != EngineUploadStyle.NONE
            val result = if (direct) {
                uploader.directSearchUrl(engine, file, ext)
            } else {
                uploader.hostTemporarily(file, ext).map { engine.searchUrl(it) }
            }
            _state.value = result.fold(
                onSuccess = { LocalSearchState.Opened(engine, it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    LocalSearchState.Failed(engine, canFallback = direct)
                },
            )
        }
    }

    /** Stops whatever is in flight and clears the sheet's state; dismissing calls this. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = LocalSearchState.Idle
    }
}
