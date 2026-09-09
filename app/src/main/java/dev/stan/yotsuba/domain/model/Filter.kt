package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.domain.model.FilterMatcher
import kotlinx.serialization.Serializable

/** Which part of a post or catalog entry a [Filter] reads. */
enum class FilterField { SUBJECT, COMMENT, NAME, TRIPCODE, FLAG, POSTER_ID, FILENAME }

/**
 * What happens to a match: gone entirely, collapsed to a one-line stub that opens on tap,
 * or left in place at low opacity so the thread still reads around it.
 */
enum class FilterAction { HIDE, STUB, FADE }

/** How many verdicts took something off the screen, for the "N filtered" count; a fade is still there. */
val Map<Long, Filter>.removedCount: Int get() = count { it.value.action != FilterAction.FADE }

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
    /**
     * Why the pattern cannot be used, or null when it is fine. Only regexes can fail. A
     * function because it compiles the pattern: callers in composition should remember it.
     */
    fun error(): String? = FilterMatcher.compile(this)?.exceptionOrNull()?.message
}
