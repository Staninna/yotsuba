package dev.stan.yotsuba.feature

import dev.stan.yotsuba.feature.media.MediaSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionStoreTest {

    @Test fun `consume returns the last-viewed post once and then clears it`() {
        val store = MediaSessionStore()
        store.setLastViewed("g", 100, 104)
        assertEquals(104L, store.consumeLastViewed("g", 100))
        assertNull(store.consumeLastViewed("g", 100))
    }

    @Test fun `entries are keyed per board and thread`() {
        val store = MediaSessionStore()
        store.setLastViewed("g", 100, 104)
        store.setLastViewed("g", 200, 205)
        store.setLastViewed("a", 100, 999)
        assertEquals(104L, store.consumeLastViewed("g", 100))
        assertEquals(205L, store.consumeLastViewed("g", 200))
        assertEquals(999L, store.consumeLastViewed("a", 100))
    }

    @Test fun `a later view overwrites the earlier one`() {
        val store = MediaSessionStore()
        store.setLastViewed("g", 100, 101)
        store.setLastViewed("g", 100, 103)
        assertEquals(103L, store.consumeLastViewed("g", 100))
    }
}
