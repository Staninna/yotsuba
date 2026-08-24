package dev.stan.yotsuba.core.util

/** Sealed network error taxonomy (§5); each maps to its own copy, icon and retry affordance. */
sealed interface NetworkError {
    data object Offline : NetworkError
    data object Timeout : NetworkError
    data object RateLimited : NetworkError
    data object NotFound : NetworkError
    data class Server(val code: Int) : NetworkError
    data class Unknown(val cause: Throwable? = null) : NetworkError
}

sealed interface DataResult<out T> {
    data class Success<T>(val value: T, val fromCache: Boolean = false) : DataResult<T>
    data class Failure(val error: NetworkError) : DataResult<Nothing>
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
