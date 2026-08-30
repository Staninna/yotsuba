package dev.stan.yotsuba.core.backup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.domain.repository.MediaVaultRepository

/** Whether the vault root on shared storage can be read and written right now. */
fun interface StorageAccessCheck {
    fun granted(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    fun storageAccessCheck(vault: MediaVaultRepository): StorageAccessCheck =
        StorageAccessCheck { vault.hasStorageAccess() }
}
