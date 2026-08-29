package dev.stan.yotsuba.dedup

import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import dev.stan.yotsuba.feature.vault.DedupPhase
import dev.stan.yotsuba.feature.vault.VaultDedupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultDedupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun entry(url: String, px: Int, bytes: Long) = DuplicateEntry(
        url = url, absolutePath = "/v/$url", displayName = url, sizeBytes = bytes,
        width = px, height = px, savedAt = 1, subject = null, isVideo = false,
    )

    private class FakeDedup(var missing: Int, private val groups: Map<DedupMode, List<DuplicateGroup>>) : VaultDedupRepository {
        var backfills = 0
        val scans = mutableListOf<Pair<DedupMode, Int>>()
        override suspend fun findByMd5(md5: String): String? = null
        override suspend fun recordMd5(url: String, md5: String) {}
        override suspend fun missingHashCount() = missing
        override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {
            backfills++
            onProgress(missing, missing)
            missing = 0
        }
        override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> {
            scans += mode to maxDistance
            return groups[mode].orEmpty()
        }
    }

    private class DeletingVault : MediaVaultRepository {
        val deleted = mutableListOf<String>()
        var failing = setOf<String>()
        override fun hasStorageAccess() = true
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun saved(): Flow<Map<String, String?>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
        override suspend fun delete(url: String): VaultError? { deleted += url; return if (url in failing) VaultError.Io("x") else null }
        override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private val group = DuplicateGroup(listOf(entry("big", 200, 5), entry("mid", 100, 4), entry("small", 50, 3)), keeperUrl = "big")

    @Test fun `start skips the backfill when nothing is missing`() = runTest {
        val dedup = FakeDedup(missing = 0, groups = mapOf(DedupMode.EXACT to listOf(group)))
        val vm = VaultDedupViewModel(dedup, DeletingVault())
        vm.start(); advanceUntilIdle()
        assertEquals(0, dedup.backfills)
        assertEquals(listOf(group), (vm.state.value.phase as DedupPhase.Ready).groups)
    }

    @Test fun `start backfills first when rows lack hashes`() = runTest {
        val dedup = FakeDedup(missing = 3, groups = emptyMap())
        val vm = VaultDedupViewModel(dedup, DeletingVault())
        vm.start(); advanceUntilIdle()
        assertEquals(1, dedup.backfills)
        assertTrue(vm.state.value.phase is DedupPhase.Ready)
    }

    @Test fun `apply group deletes everything not kept and rescans`() = runTest {
        val dedup = FakeDedup(0, mapOf(DedupMode.EXACT to listOf(group)))
        val vault = DeletingVault()
        val vm = VaultDedupViewModel(dedup, vault)
        vm.start(); advanceUntilIdle()
        vm.toggleKept(group, "mid") // keep big and mid
        vm.applyGroup(group); advanceUntilIdle()
        assertEquals(listOf("small"), vault.deleted)
        assertEquals(1, vm.state.value.lastDeleted)
        assertEquals(2, dedup.scans.size)
    }

    @Test fun `apply all removes the suggested redundant files and counts failures`() = runTest {
        val dedup = FakeDedup(0, mapOf(DedupMode.EXACT to listOf(group)))
        val vault = DeletingVault().apply { failing = setOf("small") }
        val vm = VaultDedupViewModel(dedup, vault)
        vm.start(); advanceUntilIdle()
        assertEquals(7L, vm.state.value.suggestedBytes)
        vm.applyAllSuggestions(); advanceUntilIdle()
        assertEquals(listOf("mid", "small"), vault.deleted)
        assertEquals(1, vm.state.value.lastDeleted)
        assertEquals(1, vm.state.value.lastFailed)
    }

    @Test fun `switching to similar rescans with the current distance`() = runTest {
        val dedup = FakeDedup(0, emptyMap())
        val vm = VaultDedupViewModel(dedup, DeletingVault())
        vm.start(); advanceUntilIdle()
        vm.setMaxDistance(3)
        vm.setMode(DedupMode.SIMILAR); advanceUntilIdle()
        assertEquals(DedupMode.SIMILAR to 3, dedup.scans.last())
    }
}
