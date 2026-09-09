package dev.stan.yotsuba.di

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.ReverseSearchRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.domain.repository.UsageRepository
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every fake in one place, so a test declares `@Inject lateinit var fakes: Fakes` (the
 * [dev.stan.yotsuba.FlowTest] base does) and reaches `fakes.vault`, `fakes.settings` and
 * the rest without a field per repository.
 */
@Singleton
class Fakes @Inject constructor(
    val boards: FakeBoardRepository,
    val catalog: FakeCatalogRepository,
    val threads: FakeThreadRepository,
    val bookmarks: FakeBookmarkRepository,
    val history: FakeHistoryRepository,
    val hidden: FakeHiddenThreadsRepository,
    val claimed: FakeClaimedPostRepository,
    val settings: FakeSettingsRepository,
    val maintenance: FakeMaintenanceRepository,
    val usage: FakeUsageRepository,
    val vault: FakeMediaVaultRepository,
    val saveQueue: FakeMediaSaveQueue,
    val dedup: FakeVaultDedupRepository,
    val backup: FakeBackupRepository,
    val reverseSearch: FakeReverseSearchRepository,
)

/** Replaces every production repository binding with in-memory fakes for instrumented tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {
    @Binds abstract fun boardRepository(impl: FakeBoardRepository): BoardRepository
    @Binds abstract fun catalogRepository(impl: FakeCatalogRepository): CatalogRepository
    @Binds abstract fun threadRepository(impl: FakeThreadRepository): ThreadRepository
    @Binds abstract fun bookmarkRepository(impl: FakeBookmarkRepository): BookmarkRepository
    @Binds abstract fun historyRepository(impl: FakeHistoryRepository): HistoryRepository
    @Binds abstract fun settingsRepository(impl: FakeSettingsRepository): SettingsRepository
    @Binds abstract fun mediaVaultRepository(impl: FakeMediaVaultRepository): MediaVaultRepository
    @Binds abstract fun hiddenThreadsRepository(impl: FakeHiddenThreadsRepository): HiddenThreadsRepository
    @Binds abstract fun maintenanceRepository(impl: FakeMaintenanceRepository): MaintenanceRepository
    @Binds abstract fun backupRepository(impl: FakeBackupRepository): BackupRepository
    @Binds abstract fun claimedPostRepository(impl: FakeClaimedPostRepository): ClaimedPostRepository
    @Binds abstract fun mediaSaveQueue(impl: FakeMediaSaveQueue): MediaSaveQueue
    @Binds abstract fun reverseSearchRepository(impl: FakeReverseSearchRepository): ReverseSearchRepository
    @Binds abstract fun usageRepository(impl: FakeUsageRepository): UsageRepository
}

/** The dedup binding lives in its own production module, so it needs its own replacement. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DedupModule::class])
abstract class TestDedupModule {
    @Binds abstract fun vaultDedupRepository(impl: FakeVaultDedupRepository): VaultDedupRepository
}
