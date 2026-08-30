package dev.stan.yotsuba.core.util

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import kotlin.coroutines.cancellation.CancellationException

/** Runs an API call, mapping failures to [NetworkError]; cancellation always propagates. */
suspend fun <T> apiResult(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    DataResult.Failure(e.toNetworkError())
}

fun Throwable.toNetworkError(): NetworkError = when (this) {
    is java.net.SocketTimeoutException -> NetworkError.Timeout
    is java.net.UnknownHostException, is java.net.ConnectException -> NetworkError.Offline
    is retrofit2.HttpException -> when (code()) {
        404 -> NetworkError.NotFound
        429 -> NetworkError.RateLimited
        in 500..599 -> NetworkError.Server(code())
        else -> NetworkError.Unknown(this)
    }
    else -> NetworkError.Unknown(this)
}
