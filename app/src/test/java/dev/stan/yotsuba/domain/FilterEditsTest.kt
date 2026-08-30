package dev.stan.yotsuba.domain

import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.insertFilter
import dev.stan.yotsuba.domain.model.removeFilter
import dev.stan.yotsuba.domain.model.setFilterEnabled
import dev.stan.yotsuba.domain.model.upsertFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterEditsTest {

    private val a = Filter(id = "a", pattern = "one")
    private val b = Filter(id = "b", pattern = "two")
    private val settings = Settings(filters = listOf(a, b))

    @Test fun `remove drops the filter with that id and nothing else`() {
        assertEquals(listOf(b), settings.removeFilter("a").filters)
        assertEquals(listOf(a, b), settings.removeFilter("zzz").filters)
    }

    @Test fun `insert puts an undone delete back at its old index`() {
        assertEquals(listOf(b, a), settings.removeFilter("a").insertFilter(1, a).filters)
        assertEquals(listOf(a, b), settings.removeFilter("a").insertFilter(0, a).filters)
    }

    @Test fun `insert clamps an index past the end`() {
        assertEquals(listOf(a, b), Settings(filters = listOf(a)).insertFilter(7, b).filters)
    }

    @Test fun `set enabled touches only the matching filter`() {
        val out = settings.setFilterEnabled("a", false).filters
        assertEquals(false, out[0].enabled)
        assertEquals(true, out[1].enabled)
    }

    @Test fun `upsert replaces an existing id and appends a new one`() {
        val edited = a.copy(pattern = "changed")
        assertEquals(listOf(edited, b), settings.upsertFilter(edited).filters)
        val c = Filter(id = "c", pattern = "three")
        assertEquals(listOf(a, b, c), settings.upsertFilter(c).filters)
    }
}
