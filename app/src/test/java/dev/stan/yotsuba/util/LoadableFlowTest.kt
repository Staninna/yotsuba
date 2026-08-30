package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.domain.model.DataResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadableFlowTest {

    @Test fun `flow starts as null`() = runTest {
        val loadable = LoadableFlow(backgroundScope) { DataResult.Success(1) }
        assertNull(loadable.flow.value)
        assertNull(loadable.current)
    }

    @Test fun `load sets the fetched result`() = runTest {
        val loadable = LoadableFlow(backgroundScope) { force -> DataResult.Success(if (force) "fresh" else "cached") }
        loadable.load().join()
        assertEquals(DataResult.Success("cached"), loadable.flow.value)
        assertEquals(DataResult.Success("cached"), loadable.current)
    }

    @Test fun `load passes forceRefresh through to fetch`() = runTest {
        val loadable = LoadableFlow(backgroundScope) { force -> DataResult.Success(if (force) "fresh" else "cached") }
        loadable.load(forceRefresh = true).join()
        assertEquals(DataResult.Success("fresh"), loadable.flow.value)
    }

    @Test fun `forceRefresh default keeps previous value while fetching`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val loadable = LoadableFlow(backgroundScope) { _ ->
            calls++
            if (calls > 1) gate.await()
            DataResult.Success(calls)
        }
        loadable.load().join()
        assertEquals(DataResult.Success(1), loadable.current)

        val job = loadable.load(forceRefresh = true)
        // showLoading defaults to false on forceRefresh: previous value stays visible.
        assertEquals(DataResult.Success(1), loadable.current)
        gate.complete(Unit)
        job.join()
        assertEquals(DataResult.Success(2), loadable.current)
    }

    @Test fun `showLoading true resets to null while fetching`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val loadable = LoadableFlow(backgroundScope) { _ ->
            calls++
            if (calls > 1) gate.await()
            DataResult.Success(calls)
        }
        loadable.load().join()

        val job = loadable.load(forceRefresh = true, showLoading = true)
        assertNull(loadable.current)
        gate.complete(Unit)
        job.join()
        assertEquals(DataResult.Success(2), loadable.current)
    }

    @Test fun `a newer load cancels the older one so the newest answer wins`() = runTest {
        val slow = CompletableDeferred<Unit>()
        val loadable = LoadableFlow(backgroundScope) { force ->
            if (!force) slow.await()
            DataResult.Success(if (force) "fresh" else "stale")
        }
        val first = loadable.load()
        val second = loadable.load(forceRefresh = true)
        second.join()
        slow.complete(Unit)
        first.join()
        assertTrue(first.isCancelled)
        assertEquals(DataResult.Success("fresh"), loadable.current)
    }
}
