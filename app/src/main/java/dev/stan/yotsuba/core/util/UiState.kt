package dev.stan.yotsuba.core.util

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Screen-level loading shell shared by every feature. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val error: NetworkError) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}

/** The one mapping from a [LoadableFlow] emission to the screen shell; null is still loading. */
inline fun <T, R> DataResult<T>?.toUiState(map: (T) -> R): UiState<R> = when (this) {
    null -> UiState.Loading
    is DataResult.Failure -> UiState.Error(error)
    is DataResult.Success -> UiState.Success(map(value))
}

/**
 * Holder for the per-VM "null result means loading" idiom: a [DataResult] that is refetched
 * on demand, with [flow] emitting null while a fresh load is in flight.
 */
class LoadableFlow<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (forceRefresh: Boolean) -> DataResult<T>,
) {
    private val result = MutableStateFlow<DataResult<T>?>(null)
    private var job: Job? = null

    /** null = loading. */
    val flow: StateFlow<DataResult<T>?> = result.asStateFlow()
    val current: DataResult<T>? get() = result.value

    /**
     * [showLoading] drops back to the loading state while the fetch runs. A load still in
     * flight is cancelled first, so the newest call always wins: [apiResult] rethrows
     * cancellation, and a cancelled fetch never writes its (older) answer over a newer one.
     */
    fun load(forceRefresh: Boolean = false, showLoading: Boolean = !forceRefresh): Job {
        if (showLoading) result.value = null
        job?.cancel()
        return scope.launch { result.value = fetch(forceRefresh) }.also { job = it }
    }
}
