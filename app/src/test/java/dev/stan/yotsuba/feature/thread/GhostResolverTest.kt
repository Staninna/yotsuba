package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lookup order for a ghost post: held copy, vault, live, archive; each only if it has the post. */
class GhostResolverTest {

    private val env = ThreadEnv()
    private val resolver = GhostResolver(env.vault, env.threads)
    private val ghost = env.details(listOf(ThreadEnv.post(200), ThreadEnv.post(201)), board = "b", threadNo = 200)

    private suspend fun resolve(postNo: Long = 201, skipLive: Boolean = false) =
        resolver.resolve("b", 200, postNo, skipLive = skipLive)

    @Test fun `a copy in the vault answers without touching the network`() = runTest {
        env.vault.snapshots["b" to 200L] = ghost
        val r = resolve() as DataResult.Success
        assertTrue(r.value.offlineCopy)
        assertEquals(emptyList<String>(), env.threads.asked)
    }

    @Test fun `nothing in the vault asks the live thread`() = runTest {
        env.threads.byThread["b" to 200L] = DataResult.Success(ghost)
        val r = resolve() as DataResult.Success
        assertEquals(ghost, r.value)
        assertEquals(listOf("live"), env.threads.asked)
    }

    @Test fun `a live 404 falls through to the archive`() = runTest {
        env.threads.byThread["b" to 200L] = DataResult.Failure(NetworkError.NotFound)
        env.threads.archivedByThread["b" to 200L] = DataResult.Success(ghost.copy(archive = ArchiveSource.B4K))
        val r = resolve() as DataResult.Success
        assertEquals(ArchiveSource.B4K, r.value.archive)
        assertEquals(listOf("live", "archive"), env.threads.asked)
    }

    @Test fun `nothing anywhere is not found`() = runTest {
        env.threads.byThread["b" to 200L] = DataResult.Failure(NetworkError.NotFound)
        assertEquals(DataResult.Failure(NetworkError.NotFound), resolve())
        assertEquals(listOf("live", "archive"), env.threads.asked)
    }

    @Test fun `a deadlink skips the live thread`() = runTest {
        env.threads.archivedByThread["b" to 200L] = DataResult.Success(ghost.copy(archive = ArchiveSource.DESU))
        val r = resolve(skipLive = true) as DataResult.Success
        assertEquals(ArchiveSource.DESU, r.value.archive)
        assertEquals(listOf("archive"), env.threads.asked)
    }

    @Test fun `a held copy with the post is the answer`() = runTest {
        val r = resolver.resolve("b", 200, 201, held = ghost) as DataResult.Success
        assertEquals(ghost, r.value)
        assertEquals(emptyList<String>(), env.threads.asked)
    }

    @Test fun `a copy without the post is passed over`() = runTest {
        env.vault.snapshots["b" to 200L] = ghost.copy(posts = listOf(ThreadEnv.post(200)))
        env.threads.byThread["b" to 200L] = DataResult.Success(ghost)
        val r = resolver.resolve("b", 200, 201, held = ghost.copy(posts = emptyList())) as DataResult.Success
        assertEquals(ghost, r.value)
        assertEquals(listOf("live"), env.threads.asked)
    }

    @Test fun `a live failure other than 404 is reported and the archive is not asked`() = runTest {
        env.threads.byThread["b" to 200L] = DataResult.Failure(NetworkError.Offline)
        assertEquals(DataResult.Failure(NetworkError.Offline), resolve())
        assertEquals(listOf("live"), env.threads.asked)
    }
}
