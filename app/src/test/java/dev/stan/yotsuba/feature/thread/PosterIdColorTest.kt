package dev.stan.yotsuba.feature.thread

import androidx.compose.ui.graphics.luminance
import dev.stan.yotsuba.feature.thread.components.posterIdColor
import dev.stan.yotsuba.feature.thread.components.posterIdTextColor
import org.junit.Assert.assertTrue
import org.junit.Test

/** The poster-ID pill's text must clear WCAG AA (4.5:1) over every hue the hash can produce. */
class PosterIdColorTest {

    @Test fun `pill text is legible in both themes across the hue wheel`() {
        for (dark in listOf(false, true)) {
            val text = posterIdTextColor(dark).luminance()
            idPerHue.forEach { (hue, id) ->
                val pill = posterIdColor(id, dark).luminance()
                val ratio = (maxOf(text, pill) + 0.05) / (minOf(text, pill) + 0.05)
                assertTrue("hue $hue dark=$dark ratio=$ratio", ratio >= 4.5)
            }
        }
    }

    private companion object {
        /** One ID per hue, found by hashing until every degree of the wheel has a sample. */
        val idPerHue: Map<Int, String> = (0 until 20_000).map { "id$it" }
            .groupBy { (it.hashCode().toUInt() % 360u).toInt() }
            .mapValues { (_, ids) -> ids.first() }
            .also { check(it.size == 360) }
    }
}
