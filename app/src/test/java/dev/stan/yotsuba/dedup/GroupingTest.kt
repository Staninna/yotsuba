package dev.stan.yotsuba.dedup

import dev.stan.yotsuba.core.dedup.DHash
import dev.stan.yotsuba.core.dedup.Grouping
import dev.stan.yotsuba.core.dedup.Keeper
import dev.stan.yotsuba.domain.model.DuplicateEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    private data class H(val name: String, val md5: String?, val hash: Long?)

    @Test fun `exact groups by md5 and drops singletons and unknowns`() {
        val items = listOf(H("a", "x", null), H("b", "y", null), H("c", "x", null), H("d", null, null), H("e", null, null))
        val groups = Grouping.exact(items) { it.md5 }
        assertEquals(listOf(listOf("a", "c")), groups.map { g -> g.map { it.name } })
    }

    private fun flip(h: Long, vararg bits: Int): Long = bits.fold(h) { acc, b -> acc xor (1L shl b) }

    @Test fun `near groups hashes within the distance and keeps chains together`() {
        val base = 0x5A5A_C3C3_0F0F_9696L
        val items = listOf(
            H("a", null, base),
            H("b", null, flip(base, 0, 17, 40)),            // 3 from a
            H("c", null, flip(base, 0, 17, 40, 63, 5, 22)), // 3 from b, 6 from a
            H("d", null, base.inv()),                       // 64 away
            H("e", null, flip(base, 1, 2, 3, 4, 5, 6, 7)),  // 7 from a: outside at 6
            H("f", null, null),
        )
        val groups = Grouping.near(items, maxDistance = 6) { it.hash }
        assertEquals(listOf(setOf("a", "b", "c")), groups.map { g -> g.map { it.name }.toSet() })
        val loose = Grouping.near(items, maxDistance = 7) { it.hash }
        assertEquals(setOf("a", "b", "c", "e"), loose.single().map { it.name }.toSet())
        assertTrue(Grouping.near(items, maxDistance = 0) { it.hash }.isEmpty())
    }

    @Test fun `bucketing finds every pair a brute force does`() {
        val rnd = Random(7)
        val items = ArrayList<H>()
        repeat(400) { i ->
            val h = rnd.nextLong()
            items += H("r$i", null, h)
            if (i % 5 == 0) {
                val bits = IntArray(rnd.nextInt(0, 7)) { rnd.nextInt(64) }
                items += H("n$i", null, flip(h, *bits))
            }
        }
        val threshold = 6
        val expected = HashSet<Set<String>>()
        for (i in items.indices) for (j in i + 1 until items.size) {
            if (DHash.distance(items[i].hash!!, items[j].hash!!) <= threshold) expected += setOf(items[i].name, items[j].name)
        }
        val groups = Grouping.near(items, threshold) { it.hash }
        val covered = HashSet<Set<String>>()
        for (g in groups) for (a in g) for (b in g) if (a.name < b.name) {
            if (DHash.distance(a.hash!!, b.hash!!) <= threshold) covered += setOf(a.name, b.name)
        }
        assertEquals(expected, covered)
        assertTrue(expected.isNotEmpty())
    }

    private fun entry(url: String, w: Int, h: Int, bytes: Long, savedAt: Long) = DuplicateEntry(
        url = url, absolutePath = "/v/$url", displayName = url, sizeBytes = bytes,
        width = w, height = h, savedAt = savedAt, subject = null, isVideo = false,
    )

    @Test fun `keeper prefers pixels, then bytes, then the oldest save`() {
        val small = entry("small", 100, 100, 9_000, 1)
        val bigLate = entry("bigLate", 200, 200, 5_000, 3)
        val bigHeavy = entry("bigHeavy", 200, 200, 8_000, 5)
        val bigHeavyOld = entry("bigHeavyOld", 200, 200, 8_000, 2)
        assertEquals("bigHeavyOld", Keeper.suggest(listOf(small, bigLate, bigHeavy, bigHeavyOld)).url)
        assertEquals("bigHeavy", Keeper.suggest(listOf(small, bigLate, bigHeavy)).url)
        assertEquals("bigLate", Keeper.suggest(listOf(small, bigLate)).url)
        assertEquals(listOf("bigHeavyOld", "bigHeavy", "bigLate", "small"),
            listOf(small, bigLate, bigHeavy, bigHeavyOld).sortedWith(Keeper.order).map { it.url })
    }
}
