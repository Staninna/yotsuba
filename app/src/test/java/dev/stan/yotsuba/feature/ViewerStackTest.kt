package dev.stan.yotsuba.feature

import dev.stan.yotsuba.feature.media.ViewerEntry
import dev.stan.yotsuba.feature.media.ViewerStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerStackTest {

    private fun stack(initialIndex: Int = 0, scrolls: MutableList<Int> = mutableListOf()) =
        ViewerStack(initialIndex) { scrolls += it }

    @Test
    fun `starts on the initial media page`() {
        val s = stack(initialIndex = 3)
        assertEquals(ViewerEntry.Media(3), s.top)
        assertTrue(s.onMedia)
        assertEquals(1, s.size)
    }

    @Test
    fun `push panel covers the feed`() {
        val s = stack()
        s.push(ViewerEntry.Panel(42L))
        assertEquals(ViewerEntry.Panel(42L), s.top)
        assertFalse(s.onMedia)
        assertTrue(s.navForward)
        assertEquals(1, s.panelDepth)
    }

    @Test
    fun `push media scrolls the pager`() {
        val scrolls = mutableListOf<Int>()
        val s = stack(scrolls = scrolls)
        s.push(ViewerEntry.Media(5))
        assertEquals(listOf(5), scrolls)
    }

    @Test
    fun `pop restores the media page below`() {
        val scrolls = mutableListOf<Int>()
        val s = stack(scrolls = scrolls)
        s.push(ViewerEntry.Panel(1L))
        s.push(ViewerEntry.Media(7))
        s.push(ViewerEntry.Panel(2L))
        s.pop()
        assertEquals(ViewerEntry.Media(7), s.top)
        assertFalse(s.navForward)
        assertEquals(listOf(7, 7), scrolls)
    }

    @Test
    fun `pop at the bottom is a no-op`() {
        val s = stack()
        s.pop()
        assertEquals(1, s.size)
    }

    @Test
    fun `vertical swipe rewrites the top media entry`() {
        val s = stack()
        s.syncToPage(4)
        assertEquals(ViewerEntry.Media(4), s.top)
        assertEquals(1, s.size)
    }

    @Test
    fun `sync under a panel changes nothing`() {
        val s = stack()
        s.push(ViewerEntry.Panel(9L))
        s.syncToPage(4)
        assertEquals(ViewerEntry.Panel(9L), s.top)
    }

    @Test
    fun `collapse drops every panel`() {
        val s = stack()
        s.push(ViewerEntry.Panel(1L))
        s.push(ViewerEntry.Panel(2L))
        s.collapseToMedia(6)
        assertEquals(listOf<ViewerEntry>(ViewerEntry.Media(6)), s.entries)
        assertFalse(s.navForward)
    }
}
