package dev.stan.yotsuba.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.core.database.MIGRATION_1_2
import dev.stan.yotsuba.core.database.MIGRATION_2_3
import dev.stan.yotsuba.core.database.MIGRATION_3_4
import dev.stan.yotsuba.core.database.MIGRATION_4_5
import dev.stan.yotsuba.core.database.MIGRATION_5_6
import dev.stan.yotsuba.core.database.MIGRATION_6_7
import dev.stan.yotsuba.core.database.MIGRATION_7_8
import dev.stan.yotsuba.core.database.YotsubaDatabase
import dev.stan.yotsuba.core.datastore.SettingsDataStore
import dev.stan.yotsuba.core.network.CachePolicyInterceptor
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.InMemoryCookieJar
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.network.RateLimitInterceptor
import dev.stan.yotsuba.core.network.StaleIfOfflineInterceptor
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.data.repository.BoardRepositoryImpl
import dev.stan.yotsuba.data.repository.BookmarkRepositoryImpl
import dev.stan.yotsuba.data.repository.CatalogRepositoryImpl
import dev.stan.yotsuba.data.repository.HiddenThreadsRepositoryImpl
import dev.stan.yotsuba.data.repository.HistoryRepositoryImpl
import dev.stan.yotsuba.data.repository.MaintenanceRepositoryImpl
import dev.stan.yotsuba.data.repository.MediaVaultRepositoryImpl
import dev.stan.yotsuba.data.repository.ThreadRepositoryImpl
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

private val Context.settingsStore by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    /**
     * One client shared with Coil and Media3 — same pool, dispatcher, interceptors — but its
     * Cache is 10 MB and carries API JSON only; Coil gets its own diskCache (D8).
     */
    @Provides
    @Singleton
    fun okHttpClient(
        @ApplicationContext context: Context,
        networkMonitor: NetworkMonitor,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "api_json_cache"), 10L * 1024 * 1024))
        .cookieJar(InMemoryCookieJar())
        .addInterceptor(StaleIfOfflineInterceptor { networkMonitor.current() == NetworkStatus.Offline })
        // Network interceptor so cache hits and only-if-cached requests are never throttled.
        .addNetworkInterceptor(RateLimitInterceptor())
        .addNetworkInterceptor(CachePolicyInterceptor())
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): FourChanApi = Retrofit.Builder()
        .baseUrl(Urls.API_BASE)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FourChanApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): YotsubaDatabase =
        Room.databaseBuilder(context, YotsubaDatabase::class.java, "yotsuba.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

    @Provides fun bookmarkDao(db: YotsubaDatabase) = db.bookmarkDao()
    @Provides fun historyDao(db: YotsubaDatabase) = db.historyDao()
    @Provides fun hiddenThreadDao(db: YotsubaDatabase) = db.hiddenThreadDao()
    @Provides fun downloadedMediaDao(db: YotsubaDatabase) = db.downloadedMediaDao()
    @Provides fun savedMediaDao(db: YotsubaDatabase) = db.savedMediaDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun boardRepository(impl: BoardRepositoryImpl): BoardRepository
    @Binds abstract fun catalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds abstract fun threadRepository(impl: ThreadRepositoryImpl): ThreadRepository
    @Binds abstract fun bookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
    @Binds abstract fun historyRepository(impl: HistoryRepositoryImpl): HistoryRepository
    @Binds abstract fun settingsRepository(impl: SettingsDataStore): SettingsRepository
    @Binds abstract fun mediaVaultRepository(impl: MediaVaultRepositoryImpl): MediaVaultRepository
    @Binds abstract fun hiddenThreadsRepository(impl: HiddenThreadsRepositoryImpl): HiddenThreadsRepository
    @Binds abstract fun maintenanceRepository(impl: MaintenanceRepositoryImpl): MaintenanceRepository
}
