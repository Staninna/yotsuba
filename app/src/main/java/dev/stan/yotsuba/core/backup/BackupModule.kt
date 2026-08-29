package dev.stan.yotsuba.core.backup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the process, for work no screen owns. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** Whether the vault root on shared storage can be read and written right now. */
fun interface StorageAccessCheck {
    fun granted(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun storageAccessCheck(vault: MediaVaultRepository): StorageAccessCheck =
        StorageAccessCheck { vault.hasStorageAccess() }
}
