package dev.stan.yotsuba.core.util

import kotlin.coroutines.cancellation.CancellationException

/** Runs an API call, mapping failures to [NetworkError]; cancellation always propagates. */
suspend fun <T> apiResult(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    DataResult.Failure(e.toNetworkError())
}
