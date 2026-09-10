package dev.stan.yotsuba.domain.repository

interface MaintenanceRepository {
    /** Empties every cache the app writes: the OkHttp API cache, Coil's images, the video cache. */
    suspend fun clearCaches()
}
