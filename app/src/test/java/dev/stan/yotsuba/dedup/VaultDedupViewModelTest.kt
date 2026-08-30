package dev.stan.yotsuba.dedup

import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.fake.FakeVault
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import dev.stan.yotsuba.feature.vault.DedupPhase
import dev.stan.yotsuba.feature.vault.VaultDedupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private class DeletingVault : FakeVault() {
        val deleted = mutableListOf<String>()
        var failing = setOf<String>()
        override suspend fun delete(url: String): VaultError? { deleted += url; return if (url in failing) VaultError.Io("x") else null }
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
        assertEquals(7L, vm.state.value.removalBytes)
        vm.applyAll(); advanceUntilIdle()
        assertEquals(listOf("mid", "small"), vault.deleted)
        assertEquals(1, vm.state.value.lastDeleted)
        assertEquals(1, vm.state.value.lastFailed)
    }

    @Test fun `apply all keeps whatever the user re-ticked instead of the suggestion`() = runTest {
        val dedup = FakeDedup(0, mapOf(DedupMode.EXACT to listOf(group)))
        val vault = DeletingVault()
        val vm = VaultDedupViewModel(dedup, vault)
        vm.start(); advanceUntilIdle()
        vm.toggleKept(group, "big") // untick the suggested keeper
        vm.toggleKept(group, "small") // keep the small one instead
        assertEquals(listOf("big", "mid"), vm.state.value.removals.map { it.url })
        assertEquals(9L, vm.state.value.removalBytes)
        vm.applyAll(); advanceUntilIdle()
        assertEquals(listOf("big", "mid"), vault.deleted)
    }

    @Test fun `apply all skips a group with nothing ticked`() = runTest {
        val dedup = FakeDedup(0, mapOf(DedupMode.EXACT to listOf(group)))
        val vault = DeletingVault()
        val vm = VaultDedupViewModel(dedup, vault)
        vm.start(); advanceUntilIdle()
        vm.toggleKept(group, "big")
        assertTrue(vm.state.value.removals.isEmpty())
        vm.applyAll(); advanceUntilIdle()
        assertTrue(vault.deleted.isEmpty())
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
