package dev.stan.yotsuba.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderMathTest {

    // Four tabs: |--100--|--60--|--80--|--120--|
    private val widths = listOf(100, 60, 80, 120)

    @Test fun `a small nudge stays put`() {
        assertEquals(1, dropTarget(1, 20f, widths))
        assertEquals(1, dropTarget(1, -20f, widths))
    }

    @Test fun `dragging right past a neighbour's centre takes its slot`() {
        // Tab 1 centre is at 130; tab 2's centre is at 200, so 71px moves it one slot.
        assertEquals(1, dropTarget(1, 69f, widths))
        assertEquals(2, dropTarget(1, 71f, widths))
        // Tab 3's centre is at 300.
        assertEquals(3, dropTarget(1, 171f, widths))
    }

    @Test fun `dragging left past a neighbour's centre takes its slot`() {
        // Tab 2 centre is at 200; tab 1's is at 130, tab 0's at 50.
        assertEquals(2, dropTarget(2, -69f, widths))
        assertEquals(1, dropTarget(2, -71f, widths))
        assertEquals(0, dropTarget(2, -151f, widths))
    }

    @Test fun `overshooting either edge clamps to the end tabs`() {
        assertEquals(0, dropTarget(3, -10_000f, widths))
        assertEquals(3, dropTarget(0, 10_000f, widths))
    }

    @Test fun `a single tab has nowhere to go`() {
        assertEquals(0, dropTarget(0, 500f, listOf(100)))
        assertEquals(0, dropTarget(0, 500f, emptyList()))
    }

    @Test fun `neighbours slide towards the vacated slot`() {
        assertEquals(listOf(0, -1, -1, 0), (0..3).map { shiftFor(it, 0, 2) })
        assertEquals(listOf(0, 1, 1, 0), (0..3).map { shiftFor(it, 3, 1) })
        assertEquals(listOf(0, 0, 0, 0), (0..3).map { shiftFor(it, 2, 2) })
    }

    @Test fun `the current page follows its board`() {
        assertEquals(2, remapPage(current = 0, from = 0, to = 2))
        assertEquals(0, remapPage(current = 1, from = 0, to = 2))
        assertEquals(3, remapPage(current = 3, from = 0, to = 2))
        assertEquals(2, remapPage(current = 1, from = 3, to = 1))
        assertEquals(0, remapPage(current = 0, from = 3, to = 1))
    }
}
