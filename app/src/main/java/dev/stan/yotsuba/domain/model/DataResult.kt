package dev.stan.yotsuba.domain.model

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
