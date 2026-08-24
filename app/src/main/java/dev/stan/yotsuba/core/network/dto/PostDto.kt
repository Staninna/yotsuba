package dev.stan.yotsuba.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Post numbers and image timestamps are Long, never Int: `tim` is 13-digit
 * epoch-millis and overflows Int outright (§3).
 */
@Serializable
data class PostDto(
    val no: Long,
    val resto: Long = 0,
    val time: Long = 0,
    val name: String? = null,
    val trip: String? = null,
    val id: String? = null,
    val capcode: String? = null,
    val country: String? = null,
    val country_name: String? = null,
    val board_flag: String? = null,
    val flag_name: String? = null,
    val sub: String? = null,
    val com: String? = null,
    val tim: Long? = null,
    val filename: String? = null,
    val ext: String? = null,
    val fsize: Long? = null,
    val md5: String? = null,
    val w: Int? = null,
    val h: Int? = null,
    val tn_w: Int? = null,
    val tn_h: Int? = null,
    val filedeleted: Int? = null,
    val spoiler: Int? = null,
    val custom_spoiler: Int? = null,
    val replies: Int? = null,
    val images: Int? = null,
    val omitted_posts: Int? = null,
    val omitted_images: Int? = null,
    val sticky: Int? = null,
    val closed: Int? = null,
    val archived: Int? = null,
    val archived_on: Long? = null,
    val last_modified: Long? = null,
    val semantic_url: String? = null,
    val unique_ips: Int? = null,
    val last_replies: List<PostDto>? = null,
)
