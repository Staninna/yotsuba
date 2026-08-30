package dev.stan.yotsuba.domain.repository

interface MaintenanceRepository {
    /** Evicts the OkHttp API cache and deletes the Coil image cache directory. */
    suspend fun clearCaches()
}
