package dev.stan.yotsuba.domain.model

data class Board(
    val code: String,
    val title: String,
    val description: String,
    val worksafe: Boolean,
    val category: BoardCategory,
    // Capability flags (D21) — declared data driving rendering directly.
    val userIds: Boolean,
    val countryFlags: Boolean,
    val boardFlags: Boolean,
    val spoilers: Boolean,
    val webmAudio: Boolean,
    val codeTags: Boolean,
    val mathTags: Boolean,
    val sjisTags: Boolean,
    val textOnly: Boolean,
)

enum class BoardCategory(val label: String) {
    JAPANESE_CULTURE("Japanese Culture"),
    VIDEO_GAMES("Video Games"),
    INTERESTS("Interests"),
    CREATIVE("Creative"),
    OTHER("Other"),
    MISC("Misc"),
    ADULT("Adult"),
}
