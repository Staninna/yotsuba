package dev.stan.yotsuba.domain.model

private const val EXCERPT_TITLE_LENGTH = 60

/**
 * The one title rule for a thread wherever it is listed: the subject when the source gave
 * one (even blank), else the opening of the excerpt, else [fallback] naming the thread.
 */
fun threadDisplayTitle(subject: String?, excerpt: String, fallback: String): String =
    subject ?: excerpt.take(EXCERPT_TITLE_LENGTH).ifBlank { fallback }
