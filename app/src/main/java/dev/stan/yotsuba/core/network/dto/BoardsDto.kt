package dev.stan.yotsuba.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BoardsDto(
    val boards: List<BoardDto> = emptyList(),
)

@Serializable
data class BoardDto(
    val board: String,
    val title: String,
    val ws_board: Int = 0,
    val per_page: Int = 15,
    val pages: Int = 10,
    val max_filesize: Long = 0,
    val bump_limit: Int = 0,
    val image_limit: Int = 0,
    val meta_description: String = "",
    val is_archived: Int = 0,
    // Capability flags (D21) — declared data, not inferred from posts.
    val user_ids: Int = 0,
    val country_flags: Int = 0,
    val board_flags: Map<String, String> = emptyMap(),
    val spoilers: Int = 0,
    val custom_spoilers: Int = 0,
    val webm_audio: Int = 0,
    val code_tags: Int = 0,
    val math_tags: Int = 0,
    val sjis_tags: Int = 0,
    val text_only: Int = 0,
)
