package dev.stan.yotsuba.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadSiblingsStoreTest {
    private val store = ThreadSiblingsStore().apply { record("g", listOf(10L, 20L, 30L)) }

    @Test
    fun `a thread in the middle has both neighbours`() {
        assertEquals(ThreadNeighbours(previous = 10L, next = 30L), store.neighbours("g", 20L))
    }

    @Test
    fun `the first thread has no previous`() {
        assertEquals(ThreadNeighbours(previous = null, next = 20L), store.neighbours("g", 10L))
    }

    @Test
    fun `the last thread has no next`() {
        assertEquals(ThreadNeighbours(previous = 20L, next = null), store.neighbours("g", 30L))
    }

    @Test
    fun `a thread not in the list has no entry`() {
        assertNull(store.neighbours("g", 99L))
    }

    @Test
    fun `a different board has no entry`() {
        assertNull(store.neighbours("a", 20L))
    }

    @Test
    fun `nothing recorded means no entry`() {
        assertNull(ThreadSiblingsStore().neighbours("g", 20L))
    }

    @Test
    fun `recording again replaces the previous catalog`() {
        store.record("a", listOf(1L, 2L))
        assertNull(store.neighbours("g", 20L))
        assertEquals(ThreadNeighbours(previous = 1L, next = null), store.neighbours("a", 2L))
    }

    @Test
    fun `a single thread has no neighbours either side`() {
        assertEquals(ThreadNeighbours(null, null), ThreadSiblingsStore.neighboursIn(listOf(5L), 5L))
    }
}
