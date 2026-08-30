package dev.stan.yotsuba.domain

import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.domain.model.FilterMatcher
import dev.stan.yotsuba.domain.model.FilterableFields
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FilterMatcherTest {

    private fun filter(
        pattern: String,
        isRegex: Boolean = false,
        field: FilterField = FilterField.COMMENT,
        boards: Set<String> = emptySet(),
        enabled: Boolean = true,
    ) = Filter(id = pattern, pattern = pattern, isRegex = isRegex, field = field, boards = boards, enabled = enabled)

    private fun matcher(vararg filters: Filter) = FilterMatcher(filters.toList())

    @Test fun `plain pattern is a case-insensitive substring match`() {
        val m = matcher(filter("hello"))
        assertNotNull(m.matches(FilterableFields(comment = "well HELLO there"), "g"))
        assertNull(m.matches(FilterableFields(comment = "goodbye"), "g"))
    }

    @Test fun `regex pattern matches anywhere in the field`() {
        val m = matcher(filter("^\\d+ replies$", isRegex = true))
        assertNotNull(m.matches(FilterableFields(comment = "42 replies"), "g"))
        assertNull(m.matches(FilterableFields(comment = "42 reply"), "g"))
    }

    @Test fun `plain pattern does not treat regex metacharacters specially`() {
        val m = matcher(filter("a.c"))
        assertNull(m.matches(FilterableFields(comment = "abc"), "g"))
        assertNotNull(m.matches(FilterableFields(comment = "xa.cx"), "g"))
    }

    @Test fun `board scoping limits a filter to its boards`() {
        val m = matcher(filter("spam", boards = setOf("g", "a")))
        assertNotNull(m.matches(FilterableFields(comment = "spam"), "g"))
        assertNull(m.matches(FilterableFields(comment = "spam"), "v"))
    }

    @Test fun `empty boards means every board`() {
        assertNotNull(matcher(filter("spam")).matches(FilterableFields(comment = "spam"), "v"))
    }

    @Test fun `invalid regex never matches and reports an error`() {
        val f = filter("[unclosed", isRegex = true)
        assertNotNull(f.error())
        assertNull(matcher(f).matches(FilterableFields(comment = "[unclosed"), "g"))
        assertNull(filter("[fine]", isRegex = true).error())
        assertNull(filter("[unclosed").error())
    }

    @Test fun `only the selected field is read`() {
        val m = matcher(filter("anon", field = FilterField.NAME))
        assertNotNull(m.matches(FilterableFields(name = "Anonymous"), "g"))
        assertNull(m.matches(FilterableFields(comment = "Anonymous"), "g"))
        assertNull(m.matches(FilterableFields(), "g"))
    }

    @Test fun `disabled and blank filters are skipped`() {
        val m = matcher(filter("spam", enabled = false), filter(""))
        assertNull(m.matches(FilterableFields(comment = "spam"), "g"))
        assertEquals(true, m.isEmpty)
    }

    @Test fun `first matching filter wins`() {
        val first = filter("spam")
        val m = matcher(first, filter("spam", field = FilterField.SUBJECT))
        assertEquals(first, m.matches(FilterableFields(subject = "spam", comment = "spam"), "g"))
    }

    @Test fun `thread post maps every field`() {
        val post = ThreadPost(
            board = "g", no = 1, isOp = true, name = "Anonymous", tripcode = "!abc", capcode = null,
            posterId = "Xy12", countryCode = "US", countryName = "United States", timeSeconds = 0,
            subject = "Subj", body = PostText(listOf(PostSegment("body text"))), media = null,
            quotedPostNos = emptyList(),
        )
        val fields = FilterableFields.of(post)
        assertEquals("Subj", fields[FilterField.SUBJECT])
        assertEquals("body text", fields[FilterField.COMMENT])
        assertEquals("!abc", fields[FilterField.TRIPCODE])
        assertEquals("US", fields[FilterField.FLAG])
        assertEquals("Xy12", fields[FilterField.POSTER_ID])
        assertNull(fields[FilterField.FILENAME])
    }

    @Test fun `test helper checks a sample against one pattern`() {
        assertEquals(true, FilterMatcher.test("foo", isRegex = false, sample = "FOObar"))
        assertEquals(false, FilterMatcher.test("[", isRegex = true, sample = "["))
    }
}
