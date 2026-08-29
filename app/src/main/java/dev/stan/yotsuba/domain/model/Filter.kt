package dev.stan.yotsuba.domain.model

import kotlinx.serialization.Serializable

/** Which part of a post or catalog entry a [Filter] reads. */
enum class FilterField { SUBJECT, COMMENT, NAME, TRIPCODE, FLAG, POSTER_ID, FILENAME }

/** What happens to a match: gone entirely, or collapsed to a one-line stub that opens on tap. */
enum class FilterAction { HIDE, STUB }

/**
 * A user-defined content filter. Plain patterns are case-insensitive substring matches;
 * regex patterns are compiled by the matcher, and a pattern that does not compile never
 * matches anything -- [error] is how the settings screen tells the user why.
 */
@Serializable
data class Filter(
    val id: String,
    val pattern: String,
    val isRegex: Boolean = false,
    val field: FilterField = FilterField.COMMENT,
    /** Board codes this filter applies to; empty means every board. */
    val boards: Set<String> = emptySet(),
    val action: FilterAction = FilterAction.HIDE,
    val enabled: Boolean = true,
) {
    /** Why the pattern cannot be used, or null when it is fine. Only regexes can fail. */
    val error: String?
        get() = if (isRegex) runCatching { Regex(pattern) }.exceptionOrNull()?.message else null
}
