package dev.stan.yotsuba.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Singleton
class MaintenanceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : MaintenanceRepository {

    override suspend fun clearCaches() = withContext(Dispatchers.IO) {
        runCatching { okHttpClient.cache?.evictAll() }
        File(context.cacheDir, "image_cache").deleteRecursively()
        Unit
    }
}
