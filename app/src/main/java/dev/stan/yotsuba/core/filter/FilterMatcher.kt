package dev.stan.yotsuba.core.filter

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.domain.model.ThreadPost

/**
 * The parts of a post a [Filter] can read, flattened so the catalog and the thread screen
 * hand the matcher the same shape. Absent parts are null and never match.
 */
data class FilterableFields(
    val subject: String? = null,
    val comment: String? = null,
    val name: String? = null,
    val tripcode: String? = null,
    val flag: String? = null,
    val posterId: String? = null,
    val filename: String? = null,
) {
    operator fun get(field: FilterField): String? = when (field) {
        FilterField.SUBJECT -> subject
        FilterField.COMMENT -> comment
        FilterField.NAME -> name
        FilterField.TRIPCODE -> tripcode
        FilterField.FLAG -> flag
        FilterField.POSTER_ID -> posterId
        FilterField.FILENAME -> filename
    }

    companion object {
        /** The catalog only carries subject and excerpt; the rest is unknown until the thread opens. */
        fun of(thread: CatalogThread) = FilterableFields(
            subject = thread.subject,
            comment = thread.excerpt.plainText,
        )

        fun of(post: ThreadPost) = FilterableFields(
            subject = post.subject,
            comment = post.body.plainText,
            name = post.name,
            tripcode = post.tripcode,
            flag = post.countryCode ?: post.countryName,
            posterId = post.posterId,
            filename = post.presentMedia?.displayName,
        )
    }
}

/**
 * Pure matcher over one settings emission. Build it once per `Settings.filters` value and
 * reuse it for every row: regexes compile here, not per post.
 */
class FilterMatcher(filters: List<Filter>) {

    private class Compiled(val filter: Filter, val regex: Regex?) {
        fun matches(value: String): Boolean =
            if (filter.isRegex) regex?.containsMatchIn(value) == true
            else value.contains(filter.pattern, ignoreCase = true)
    }

    private val compiled: List<Compiled> = filters
        .filter { it.enabled && it.pattern.isNotEmpty() }
        .map { f ->
            // A broken regex compiles to null and so never matches; Filter.error() reports it.
            Compiled(f, compile(f)?.getOrNull())
        }

    val isEmpty: Boolean get() = compiled.isEmpty()

    /** The first enabled filter that applies to [board] and matches [fields], or null. */
    fun matches(fields: FilterableFields, board: String): Filter? = compiled.firstOrNull { c ->
        (c.filter.boards.isEmpty() || board in c.filter.boards) &&
            fields[c.filter.field]?.let(c::matches) == true
    }?.filter

    companion object {
        val Empty = FilterMatcher(emptyList())

        /**
         * The regex a filter runs, or the failure explaining why it cannot; null for plain
         * substring filters. The one place a pattern's language is decided.
         */
        fun compile(filter: Filter): Result<Regex>? =
            if (filter.isRegex) runCatching { Regex(filter.pattern) } else null

        /** Whether [sample] would be caught by a filter with these settings; for the settings "Test" field. */
        fun test(pattern: String, isRegex: Boolean, sample: String): Boolean =
            FilterMatcher(listOf(Filter(id = "test", pattern = pattern, isRegex = isRegex)))
                .matches(FilterableFields(comment = sample), board = "") != null
    }
}
